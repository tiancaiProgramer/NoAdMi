"""Embed our own scoped WebView script so the target Context never reads module assets."""
from pathlib import Path
import json
root=Path(__file__).resolve().parents[1]
script=(root/'src/main/assets/market-detail.js').read_text(encoding='utf-8').strip()
output=root/'src/main/java/io/github/miclean/module/MarketDetailScript.java'
source='package io.github.miclean.module;\n\n// Generated from src/main/assets/market-detail.js by tools/embed_market_detail.py.\nfinal class MarketDetailScript {\n    static String source(){return '+json.dumps(script,ensure_ascii=False)+';}\n    private MarketDetailScript(){}\n}\n'
if not output.exists() or output.read_text(encoding='utf-8')!=source:output.write_text(source,encoding='utf-8')
