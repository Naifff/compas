input.onGesture(Gesture.Shake, function () {
    input.calibrateCompass()
})
let курс = 0
let стороны_саета = [
images.arrowImage(ArrowNames.North),
images.arrowImage(ArrowNames.NorthEast),
images.arrowImage(ArrowNames.East),
images.arrowImage(ArrowNames.SouthEast),
images.arrowImage(ArrowNames.South),
images.arrowImage(ArrowNames.SouthWest),
images.arrowImage(ArrowNames.West),
images.arrowImage(ArrowNames.NorthWest),
images.arrowImage(ArrowNames.North)
]
basic.forever(function () {
    курс = input.compassHeading()
    стороны_саета[Math.round(курс / 45)].showImage(0)
})
