# driverAppPrototype

## Part of a larger project
At the moment the project is called **Unnamed Driver's App**.

## Aim of this app
Estimate if an average smartphone has enough computational power to use yolov5 efficiently.

## Design and manual
### Prompt
After starting the app, you'll see a prompt asking you to choose a certain YOLO model, you can easily modify the available options in code by adding your model to the assets folder (in .torchscript.ptl format), entering the file names into the modelList and adding your option to the AlertDialog (just remember to add it before the "Same as before" option.
![prompt](https://github.com/AntekBrudka/driverAppPrototype/assets/45321229/cd07452c-60ce-49f0-a5a0-2f6a8a151137)


### Choose a video
When clicking on the middle button, a prompt will show up, asking you to choose a video as a source.
![chooseVideo](https://github.com/AntekBrudka/driverAppPrototype/assets/45321229/e118ad40-ce57-4d8e-b355-875aa8d3bdac)

### Text feedback
The right button enables you to choose the feedback mode, either visual (in the upper imageView) or text (in the middle textView).
![detText](https://github.com/AntekBrudka/driverAppPrototype/assets/45321229/d957144a-8239-4204-8343-29ded02d932d)

### Visual feedback
![detVisual](https://github.com/AntekBrudka/driverAppPrototype/assets/45321229/759528f4-672c-4bff-b6a2-0079f07815ec)

## Plans
Using camera as a source will be added to the app, till then, the source button on the left is not doing anything.

### Disclaimer
Some functions are based on the [Android demo app]([github.com/pytorch/android-demo-app](https://github.com/pytorch/android-demo-app)https://github.com/pytorch/android-demo-app).
