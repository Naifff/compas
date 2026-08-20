package bench.task03;

import java.io.Closeable;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * A bounded on-disk cache with a background eviction thread.
 *
 * <p>Values are stored as individual files under a directory; the index of
 * live entries is kept in memory in access order, so that the least recently
 * used entry is the first candidate for eviction. A background sweeper drops
 * index entries whose backing file has disappeared and re-applies the size
 * limit periodically.
 *
 * <p>This class has been in production for a long time. Recent incident
 * reports mention corrupted reads under load, the on-disk footprint drifting
 * over the configured limit, and file descriptors accumulating until the
 * process cannot open new ones.
 */
public final class FileCache implements Closeable {

    /** How often the background sweeper runs. */
    private static final long SWEEP_INTERVAL_MILLIS = 50L;

    /** One cached value: where it lives and how big it is. */
    private static final class Entry {
        final String fileName;
        final long size;

        Entry(String fileName, long size) {
            this.fileName = fileName;
            this.size = size;
        }
    }

    /**
     * A channel that knows how to unregister itself.
     *
     * <p>The registry is what the {@code openHandleCount()} gauge reports, so
     * a channel that is never closed stays visible to monitoring.
     */
    private final class TrackedChannel implements Closeable {
        private final FileChannel channel;

        TrackedChannel(FileChannel channel) {
            this.channel = channel;
            synchronized (openChannels) {
                openChannels.add(this);
            }
        }

        FileChannel channel() {
            return channel;
        }

        @Override
        public void close() throws IOException {
            synchronized (openChannels) {
                openChannels.remove(this);
            }
            channel.close();
        }
    }

    private final Path directory;
    private final long maxBytes;

    /** Access-ordered index: eldest entry is the least recently used. */
    private final LinkedHashMap<String, Entry> index =
            new LinkedHashMap<String, Entry>(16, 0.75f, true);

    private final Object lock = new Object();
    private final Set<TrackedChannel> openChannels = new HashSet<TrackedChannel>();
    private final Thread sweeper;

    private volatile boolean closed;
    private long currentBytes;

    /**
     * @param directory where value files are written; created if absent
     * @param maxBytes  the total size the stored values must never exceed
     */
    public FileCache(Path directory, long maxBytes) throws IOException {
        if (maxBytes <= 0) {
            throw new IllegalArgumentException("maxBytes must be positive");
        }
        this.directory = directory;
        this.maxBytes = maxBytes;
        Files.createDirectories(directory);

        this.sweeper = new Thread(new Runnable() {
            @Override
            public void run() {
                sweepLoop();
            }
        }, "file-cache-sweeper");
        this.sweeper.setDaemon(true);
        this.sweeper.start();
    }

    /**
     * Stores a value, evicting least recently used entries if needed.
     *
     * @throws IllegalArgumentException if the value alone exceeds the limit
     * @throws IllegalStateException    if the cache is closed
     */
    public void put(String key, byte[] value) throws IOException {
        checkOpen();
        if (key == null) {
            throw new NullPointerException("key");
        }
        if (value == null) {
            throw new NullPointerException("value");
        }
        if (value.length > maxBytes) {
            throw new IllegalArgumentException(
                    "value of " + value.length + " bytes exceeds maxBytes=" + maxBytes);
        }

        String fileName = fileNameFor(key);
        Path target = directory.resolve(fileName);

        synchronized (lock) {
            Entry previous = index.remove(key);
            if (previous != null) {
                currentBytes -= previous.size;
            }
            evictToFit(value.length);
        }

        writeFile(target, value);

        synchronized (lock) {
            index.put(key, new Entry(fileName, value.length));
            currentBytes += value.length;
        }
    }

    /**
     * Returns the latest fully written value, or null if the key is absent.
     *
     * @throws IllegalStateException if the cache is closed
     */
    public byte[] get(String key) throws IOException {
        checkOpen();
        if (key == null) {
            throw new NullPointerException("key");
        }

        Entry entry = index.get(key);
        if (entry == null) {
            return null;
        }
        Path target = directory.resolve(entry.fileName);
        if (!Files.exists(target)) {
            return null;
        }
        return readFile(target, entry.size);
    }

    /**
     * Removes a value.
     *
     * @return true if something was removed
     * @throws IllegalStateException if the cache is closed
     */
    public boolean remove(String key) throws IOException {
        checkOpen();
        Entry entry;
        synchronized (lock) {
            entry = index.remove(key);
            if (entry == null) {
                return false;
            }
            currentBytes -= entry.size;
        }
        Files.deleteIfExists(directory.resolve(entry.fileName));
        return true;
    }

    /** Total size of the stored values, in bytes. */
    public long currentBytes() {
        synchronized (lock) {
            return currentBytes;
        }
    }

    /** Number of live entries. */
    public int entryCount() {
        synchronized (lock) {
            return index.size();
        }
    }

    /**
     * Number of file handles the cache currently holds open.
     *
     * <p>Monitoring alerts when this does not return to zero while the cache
     * is idle.
     */
    public int openHandleCount() {
        synchronized (openChannels) {
            return openChannels.size();
        }
    }

    /** Stops the background thread and releases resources. */
    @Override
    public void close() {
        if (closed) {
            return;
        }
        closed = true;
        sweeper.interrupt();
        try {
            sweeper.join(1000L);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        List<TrackedChannel> leftovers;
        synchronized (openChannels) {
            leftovers = new ArrayList<TrackedChannel>(openChannels);
        }
        for (TrackedChannel channel : leftovers) {
            try {
                channel.close();
            } catch (IOException ignored) {
                // Nothing useful to do while shutting down.
            }
        }
        synchronized (lock) {
            index.clear();
            currentBytes = 0;
        }
    }

    // ---------------------------------------------------------------- internals

    private void checkOpen() {
        if (closed) {
            throw new IllegalStateException("cache is closed");
        }
    }

    /** Evicts least recently used entries until the incoming value fits. */
    private void evictToFit(long incoming) throws IOException {
        Iterator<Map.Entry<String, Entry>> it = index.entrySet().iterator();
        while (currentBytes + incoming - 1 > maxBytes && it.hasNext()) {
            Map.Entry<String, Entry> eldest = it.next();
            it.remove();
            currentBytes -= eldest.getValue().size;
            Files.deleteIfExists(directory.resolve(eldest.getValue().fileName));
        }
    }

    private void writeFile(Path target, byte[] value) throws IOException {
        TrackedChannel tracked = new TrackedChannel(FileChannel.open(target,
                StandardOpenOption.CREATE,
                StandardOpenOption.WRITE,
                StandardOpenOption.TRUNCATE_EXISTING));
        try {
            ByteBuffer buffer = ByteBuffer.wrap(value);
            while (buffer.hasRemaining()) {
                tracked.channel().write(buffer);
            }
            tracked.channel().force(true);
        } finally {
            tracked.close();
        }
    }

    private byte[] readFile(Path target, long expectedSize) throws IOException {
        TrackedChannel tracked = new TrackedChannel(
                FileChannel.open(target, StandardOpenOption.READ));
        ByteBuffer buffer = ByteBuffer.allocate((int) expectedSize);
        while (buffer.hasRemaining()) {
            int read = tracked.channel().read(buffer);
            if (read < 0) {
                throw new IOException("cache file is shorter than its index entry: "
                        + target);
            }
        }
        tracked.close();
        return buffer.array();
    }

    private static String fileNameFor(String key) {
        StringBuilder sb = new StringBuilder();
        for (byte b : key.getBytes(StandardCharsets.UTF_8)) {
            sb.append(Character.forDigit((b >> 4) & 0xF, 16));
            sb.append(Character.forDigit(b & 0xF, 16));
        }
        return sb.append(".val").toString();
    }

    private void sweepLoop() {
        while (!closed && !Thread.currentThread().isInterrupted()) {
            try {
                Thread.sleep(SWEEP_INTERVAL_MILLIS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
            try {
                sweepOnce();
            } catch (IOException ignored) {
                // A transient filesystem problem must not kill the sweeper.
            }
        }
    }

    /** Drops entries whose file vanished, then re-applies the size limit. */
    private void sweepOnce() throws IOException {
        List<Path> doomed = new ArrayList<Path>();
        synchronized (lock) {
            Iterator<Map.Entry<String, Entry>> it = index.entrySet().iterator();
            while (it.hasNext()) {
                Map.Entry<String, Entry> candidate = it.next();
                Path target = directory.resolve(candidate.getValue().fileName);
                if (!Files.exists(target)) {
                    it.remove();
                    currentBytes -= candidate.getValue().size;
                }
            }
            Iterator<Map.Entry<String, Entry>> overflow = index.entrySet().iterator();
            while (currentBytes > maxBytes && overflow.hasNext()) {
                Map.Entry<String, Entry> eldest = overflow.next();
                overflow.remove();
                currentBytes -= eldest.getValue().size;
                doomed.add(directory.resolve(eldest.getValue().fileName));
            }
        }
        for (Path path : doomed) {
            Files.deleteIfExists(path);
        }
    }

    /** Snapshot of the live keys, in least-recently-used order. */
    public List<String> keys() {
        synchronized (lock) {
            return Collections.unmodifiableList(new ArrayList<String>(index.keySet()));
        }
    }
}
