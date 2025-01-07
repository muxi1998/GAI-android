#include "llava_runner_simplified.h"

namespace example {

struct LlavaRunner::Impl {
    bool is_loaded = false;
    std::string model_path;
    std::string tokenizer_path;
    float temperature;
};

LlavaRunner::LlavaRunner(
    const std::string& model_path,
    const std::string& tokenizer_path,
    float temperature)
    : pImpl(std::make_unique<Impl>()) {
    pImpl->model_path = model_path;
    pImpl->tokenizer_path = tokenizer_path;
    pImpl->temperature = temperature;
}

LlavaRunner::~LlavaRunner() = default;

Result LlavaRunner::load() {
    // Simplified implementation
    pImpl->is_loaded = true;
    return Result(true);
}

bool LlavaRunner::is_loaded() const {
    return pImpl->is_loaded;
}

void LlavaRunner::generate(
    const std::vector<Image>& images,
    const std::string& prompt,
    int32_t seq_len,
    std::function<void(const std::string&)> token_callback,
    std::function<void(const std::string&)> stats_callback) {
    // Simplified implementation
    if (token_callback) {
        token_callback("Sample response");
    }
}

} // namespace example 