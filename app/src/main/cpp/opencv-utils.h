#pragma once

#include <opencv2/core.hpp>

using namespace cv;

Mat detect(Mat src);
std::vector<std::string> load_class_list();
