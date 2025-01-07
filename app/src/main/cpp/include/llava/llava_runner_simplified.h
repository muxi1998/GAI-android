#pragma once

#include <cstdint>
#include <functional>
#include <memory>
#include <string>
#include <vector>

namespace example {

// Define Result type
class Result {
public:
    bool ok() const { return success_; }
    explicit Result(bool success = true) : success_(success) {}
private:
    bool success_;
};

// Define Image structure
struct Image {
    uint8_t* data;
    int32_t width;
    int32_t height;
};

class LlavaRunner {
public:
    explicit LlavaRunner(
        const std::string& model_path,
        const std::string& tokenizer_path,
        float temperature = 0.8f);
    
    ~LlavaRunner(); // Add destructor declaration

    Result load();
    bool is_loaded() const;

    void generate(
        const std::vector<Image>& images,
        const std::string& prompt,
        int32_t seq_len,
        std::function<void(const std::string&)> token_callback,
        std::function<void(const std::string&)> stats_callback = nullptr);

private:
    struct Impl; // Change from class to struct
    std::unique_ptr<Impl> pImpl;
};

} // namespace example 