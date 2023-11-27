#include "opencv-utils.h"
#include <opencv2/imgproc.hpp>
#include <opencv2/opencv.hpp>
#include <fstream>


std::vector<std::string> load_class_list()
{
    std::vector<std::string> class_list;
    std::fstream newfile;
    newfile.open(R"(C:\Users\Antek\AndroidStudioProjects\driverAppPrototype\app\src\main\cpp\classes.txt)");
    if(newfile.is_open())
    {
        std::string line;
        while(getline(newfile, line)){
            class_list.push_back(line);
        }
    }
    return class_list;
}

Mat detect(Mat src) {
    flip(src, src, 0);
    cv::Mat output;
    // AKTUALNIE NIE MOŻE ZNALEŹĆ PLIKU SPRAWDZIĆ JAK ZROBIĆ ŻEBY MÓGŁ WIDZIEĆ TE PLIKI (odpowiedź w pliku howtoincludeitw resforapp)
    try {
        std::vector<std::string> class_list = load_class_list();
    }
    catch (cv::Exception& e) {
        std::cerr << "OpenCV Error: " << e.what() << std::endl;
        // Additional error handling if needed
    }
//    auto model = cv::dnn::readNet(R"(weights_and_biases.onnx)");
//    std::vector<std::string> class_list = load_class_list();
//    cv::Mat blob;
//    // cv:dnn...Image(input, output, salefactor for pixel values (here it's from 0-255 to 0-1), size which the NN has to have, scalar
//    cv::dnn::blobFromImage(src, blob, 1./255., cv::Size(640, 640));
//
//    model.setInput(blob);
//    std::vector<cv::Mat> predictions;
//    model.forward(predictions, model.getUnconnectedOutLayersNames());
//    cv::Mat output = predictions[0];

    return output;
}