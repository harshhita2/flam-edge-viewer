#include <jni.h>
#include <stdint.h>
#include <vector>
#include <algorithm>
#include <cmath>

extern "C"
JNIEXPORT jbyteArray JNICALL
Java_com_example_edgeviewer_MainActivity_processFrameNV21(
        JNIEnv *env,
        jobject thiz,
        jbyteArray nv21Data,
        jint width,
        jint height) {

    jbyte *nv21 = env->GetByteArrayElements(nv21Data, nullptr);

    int frameSize = width * height;

    // Output RGBA buffer
    std::vector<uint8_t> rgba(frameSize * 4);

    // Very simple luminance-based edges
    for (int y = 1; y < height - 1; y++) {
        for (int x = 1; x < width - 1; x++) {

            int idx = y * width + x;

            int gx =
                    nv21[idx - 1] -
                    nv21[idx + 1];

            int gy =
                    nv21[idx - width] -
                    nv21[idx + width];

            int mag = std::min(255, std::abs(gx) + std::abs(gy));

            int out = idx * 4;
            rgba[out + 0] = mag;  // R
            rgba[out + 1] = mag;  // G
            rgba[out + 2] = mag;  // B
            rgba[out + 3] = 255;  // A
        }
    }

    jbyteArray result = env->NewByteArray(rgba.size());
    env->SetByteArrayRegion(result, 0, rgba.size(), (jbyte *) rgba.data());

    env->ReleaseByteArrayElements(nv21Data, nv21, 0);

    return result;
}
