"""index.html과 test-tools.js로 테스트 서버용 test.html을 만든다.

사용법: python3 tools/build_test.py
"""
from pathlib import Path

root = Path(__file__).resolve().parent.parent
game = (root / "index.html").read_text(encoding="utf-8")
tools = (root / "test-tools.js").read_text(encoding="utf-8")

page = game.replace("<title>도시의 한 생애</title>", "<title>도시의 한 생애 테스트판</title>", 1)
page = page.replace('<script>\n"use strict";', '<script>window.CITY_TEST = true;</script>\n<script>\n"use strict";', 1)
page = page.replace("</body>", f"<script>\n{tools}</script>\n</body>", 1)
assert "window.CITY_TEST = true" in page and "테스트 도구" in page
(root / "test.html").write_text(page, encoding="utf-8")
print("test.html 생성")
