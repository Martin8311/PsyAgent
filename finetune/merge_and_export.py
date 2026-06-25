"""
合并 LoRA adapter 到基座并导出为完整 HF 模型(供转 GGUF)。

前置：train_qlora.py 已生成 out/adapter/
用法：python merge_and_export.py
产出：out/merged/  (可直接喂给 llama.cpp 的 convert_hf_to_gguf.py)

注意：合并时用 fp16 全精度加载(非 4bit)，需要约 6~7GB 显存或走 CPU(--cpu)。
"""
import argparse
import torch
from transformers import AutoModelForCausalLM, AutoTokenizer
from peft import PeftModel

BASE_MODEL = "Qwen/Qwen2.5-3B-Instruct"
ADAPTER = "out/adapter"
MERGED = "out/merged"


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--cpu", action="store_true", help="显存不足时用 CPU 合并(较慢)")
    args = ap.parse_args()

    device_map = "cpu" if args.cpu else "auto"
    base = AutoModelForCausalLM.from_pretrained(
        BASE_MODEL, torch_dtype=torch.float16, device_map=device_map
    )
    model = PeftModel.from_pretrained(base, ADAPTER)
    model = model.merge_and_unload()
    model.save_pretrained(MERGED, safe_serialization=True)
    AutoTokenizer.from_pretrained(ADAPTER).save_pretrained(MERGED)
    print(f"\n✓ 已合并导出到 {MERGED}/，下一步用 llama.cpp 转 GGUF(见 README)")


if __name__ == "__main__":
    main()
