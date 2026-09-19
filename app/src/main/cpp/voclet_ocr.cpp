// ncnn behind four JNI calls. Everything that decides *what* the models see -
// resizing the page, planning the crops, warping them - stays in Kotlin, where
// it is already pinned against the reference implementation. This file only
// runs a net.
//
// The input is raw RGB rather than a float tensor: ncnn's from_pixels plus
// substract_mean_normalize is the path the host harness validated, and it
// hands 5.8 MB across JNI for a full page where a float tensor would hand 23.
#include <jni.h>

#include <net.h>

#include <string>
#include <vector>

namespace {

struct Model {
    ncnn::Net net;
};

jlong asHandle(Model* model) { return reinterpret_cast<jlong>(model); }

Model* asModel(jlong handle) { return reinterpret_cast<Model*>(handle); }

}  // namespace

extern "C" {

JNIEXPORT jlong JNICALL
Java_com_github_mwiest_voclet_data_ai_ocr_NcnnNet_nativeOpen(
    JNIEnv* env, jclass, jstring paramPath, jstring binPath) {
    const char* param = env->GetStringUTFChars(paramPath, nullptr);
    const char* bin = env->GetStringUTFChars(binPath, nullptr);

    auto* model = new Model();
    model->net.opt.use_vulkan_compute = false;
    const int loadedParam = model->net.load_param(param);
    const int loadedBin = model->net.load_model(bin);

    env->ReleaseStringUTFChars(paramPath, param);
    env->ReleaseStringUTFChars(binPath, bin);

    if (loadedParam != 0 || loadedBin != 0) {
        delete model;
        return 0;
    }
    return asHandle(model);
}

JNIEXPORT void JNICALL
Java_com_github_mwiest_voclet_data_ai_ocr_NcnnNet_nativeClose(JNIEnv*, jclass, jlong handle) {
    delete asModel(handle);
}

/**
 * Runs one RGB image through the net.
 *
 * [outShape] is filled with the output's width, height and channel count, so
 * the caller can reshape the flat result. Returns null if the net refuses.
 */
JNIEXPORT jfloatArray JNICALL
Java_com_github_mwiest_voclet_data_ai_ocr_NcnnNet_nativeRun(
    JNIEnv* env, jclass, jlong handle, jbyteArray rgb, jint width, jint height,
    jfloatArray meanValues, jfloatArray normValues, jintArray outShape) {
    Model* model = asModel(handle);
    if (model == nullptr) return nullptr;

    jbyte* pixels = static_cast<jbyte*>(env->GetPrimitiveArrayCritical(rgb, nullptr));
    if (pixels == nullptr) return nullptr;
    ncnn::Mat in = ncnn::Mat::from_pixels(
        reinterpret_cast<const unsigned char*>(pixels), ncnn::Mat::PIXEL_RGB, width, height);
    env->ReleasePrimitiveArrayCritical(rgb, pixels, JNI_ABORT);

    float mean[3];
    float norm[3];
    env->GetFloatArrayRegion(meanValues, 0, 3, mean);
    env->GetFloatArrayRegion(normValues, 0, 3, norm);
    in.substract_mean_normalize(mean, norm);

    ncnn::Extractor ex = model->net.create_extractor();
    if (ex.input("in0", in) != 0) return nullptr;
    ncnn::Mat out;
    if (ex.extract("out0", out) != 0) return nullptr;

    const jint shape[3] = {out.w, out.h, out.c};
    env->SetIntArrayRegion(outShape, 0, 3, shape);

    const int total = out.w * out.h * out.c;
    jfloatArray result = env->NewFloatArray(total);
    if (result == nullptr) return nullptr;
    // Mat rows are padded to the allocator's alignment, so copy per channel
    // rather than trusting out.data to be contiguous.
    for (int c = 0; c < out.c; c++) {
        env->SetFloatArrayRegion(result, c * out.w * out.h, out.w * out.h, out.channel(c));
    }
    return result;
}

}  // extern "C"
