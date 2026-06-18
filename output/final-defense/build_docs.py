from __future__ import annotations

from pathlib import Path
from typing import Iterable

from PIL import Image, ImageDraw, ImageFont
from docx import Document
from docx.enum.section import WD_SECTION
from docx.enum.text import WD_ALIGN_PARAGRAPH
from docx.enum.table import WD_CELL_VERTICAL_ALIGNMENT
from docx.oxml import OxmlElement
from docx.oxml.ns import qn
from docx.shared import Inches, Pt, RGBColor


ROOT = Path(__file__).resolve().parents[2]
OUT = ROOT / "output" / "final-defense"
IMG = OUT / "images"
OUT.mkdir(parents=True, exist_ok=True)
IMG.mkdir(parents=True, exist_ok=True)

ACCENT = RGBColor(20, 125, 113)
INK = RGBColor(15, 23, 42)
MUTED = RGBColor(100, 116, 139)
LIGHT = "F2F4F7"


FACTS = {
    "baseline": "94e7eff Accept PR2 workflow improvements（2026-06-08）",
    "head": "07327ba Add resumable attachment chunk upload（2026-06-17）",
    "diff": "相比中期后基准，131 个文件变更，新增 10393 行，删除 1175 行。",
    "tests": "后端 mvn test 文档记录为 82 个测试通过；workflow_sim.py 在轮次上限 4 时遍历 24613 个可达状态，非截断死锁为 0。",
    "members": "PPT 第 13 页给出团队分工和真实姓名：赵杰雄、谷友瑄、王凯、项重善、张浩然；报告按 PPT 出现顺序映射到项目负责人、后端开发、前端开发、测试、数据库/运维。",
}

CODE_STATS = {
    "total_files": 186,
    "total_lines": 16739,
    "areas": [
        ("后端工程", "backend", 132, 8429, "50.4%", "Spring Boot 业务、权限、工作流、附件、测试与数据库迁移"),
        ("前端工程", "frontend", 27, 4143, "24.7%", "Vue 3 页面、路由权限、合同流程交互和样式"),
        ("项目文档", "docs", 17, 2177, "13.0%", "计划、架构、测试问题、扩展设计和代码审查记录"),
        ("脚本工具", "scripts", 7, 1532, "9.2%", "一键启动、停止、演示数据、状态机验证和冒烟检查"),
        ("根目录配置", "docker-compose.yml / README 等", 3, 458, "2.7%", "部署编排、项目说明和运行配置"),
    ],
    "languages": [
        ("Java", ".java", 115, 7641, "45.6%", "后端业务主体、测试和安全配置"),
        ("Vue", ".vue", 19, 3018, "18.0%", "前端页面和组件"),
        ("Markdown", ".md", 19, 2539, "15.2%", "过程文档与答辩素材依据"),
        ("PowerShell", ".ps1", 6, 1173, "7.0%", "启动、停止、冒烟和演示辅助脚本"),
        ("CSS", ".css", 1, 897, "5.4%", "统一页面样式"),
        ("SQL", ".sql", 15, 558, "3.3%", "Flyway 多数据库迁移"),
        ("Python", ".py", 1, 359, "2.1%", "状态机 BFS 验证脚本"),
        ("JavaScript/YAML/XML/HTML", ".js/.yml/.xml/.html", 10, 554, "3.3%", "前端入口、配置和构建描述"),
    ],
}

TEAM_MEMBERS = [
    {
        "role": "项目负责人",
        "name": "赵杰雄",
        "ratio": "22%",
        "tasks": "范围确认、进度统筹、风险管理、文档整合、答辩组织",
        "deliverables": "启动/关闭报告、结题 PPT、演示视频组织、里程碑与验收材料",
    },
    {
        "role": "后端开发",
        "name": "谷友瑄",
        "ratio": "22%",
        "tasks": "Spring Boot 工程、JWT/RBAC、合同状态机、附件上传、模板和统计接口",
        "deliverables": "后端接口、领域服务、数据库迁移、权限校验、集成测试",
    },
    {
        "role": "前端开发",
        "name": "王凯",
        "ratio": "21%",
        "tasks": "Vue 页面、路由权限、合同详情、流程可视化、查询统计和演示素材",
        "deliverables": "前端页面、组件、交互状态、截图素材和用户手册配图",
    },
    {
        "role": "测试",
        "name": "项重善",
        "ratio": "18%",
        "tasks": "测试用例、回归验证、Playwright 演示脚本、缺陷复核和验收记录",
        "deliverables": "测试报告、回归清单、自动化验证记录、演示流程确认",
    },
    {
        "role": "数据库/运维",
        "name": "张浩然",
        "ratio": "17%",
        "tasks": "Flyway 迁移、Docker Compose、启动停止脚本、演示数据和部署说明",
        "deliverables": "数据库脚本、Docker 编排、start-demo/stop-demo、部署与运维说明",
    },
]


def set_cell_shading(cell, fill: str) -> None:
    tc_pr = cell._tc.get_or_add_tcPr()
    shd = OxmlElement("w:shd")
    shd.set(qn("w:fill"), fill)
    tc_pr.append(shd)


def set_cell_text(cell, text: str, bold: bool = False) -> None:
    cell.text = ""
    p = cell.paragraphs[0]
    run = p.add_run(text)
    run.bold = bold
    run.font.size = Pt(9)
    cell.vertical_alignment = WD_CELL_VERTICAL_ALIGNMENT.CENTER


def style_doc(doc: Document, title: str) -> None:
    section = doc.sections[0]
    section.top_margin = Inches(0.85)
    section.bottom_margin = Inches(0.8)
    section.left_margin = Inches(0.85)
    section.right_margin = Inches(0.85)
    styles = doc.styles
    normal = styles["Normal"]
    normal.font.name = "Microsoft YaHei"
    normal._element.rPr.rFonts.set(qn("w:eastAsia"), "Microsoft YaHei")
    normal.font.size = Pt(10.5)
    for name, size, color in [("Heading 1", 16, ACCENT), ("Heading 2", 13, INK), ("Heading 3", 11.5, INK)]:
        st = styles[name]
        st.font.name = "Microsoft YaHei"
        st._element.rPr.rFonts.set(qn("w:eastAsia"), "Microsoft YaHei")
        st.font.size = Pt(size)
        st.font.color.rgb = color
        st.font.bold = True
    footer = section.footer.paragraphs[0]
    footer.text = f"ContractSys 合同管理系统 | {title}"
    footer.alignment = WD_ALIGN_PARAGRAPH.CENTER
    footer.runs[0].font.size = Pt(8)
    footer.runs[0].font.color.rgb = MUTED


def add_title(doc: Document, title: str, subtitle: str) -> None:
    p = doc.add_paragraph()
    p.alignment = WD_ALIGN_PARAGRAPH.CENTER
    r = p.add_run(title)
    r.font.size = Pt(22)
    r.font.bold = True
    r.font.color.rgb = INK
    p2 = doc.add_paragraph()
    p2.alignment = WD_ALIGN_PARAGRAPH.CENTER
    r2 = p2.add_run(subtitle)
    r2.font.size = Pt(11)
    r2.font.color.rgb = MUTED
    doc.add_paragraph()


def add_table(doc: Document, headers: list[str], rows: Iterable[Iterable[str]]) -> None:
    rows = list(rows)
    table = doc.add_table(rows=len(rows) + 1, cols=len(headers))
    table.style = "Table Grid"
    table.autofit = True
    for i, header in enumerate(headers):
        set_cell_shading(table.rows[0].cells[i], LIGHT)
        set_cell_text(table.rows[0].cells[i], header, True)
    for r_i, row in enumerate(rows, start=1):
        for c_i, value in enumerate(row):
            set_cell_text(table.rows[r_i].cells[c_i], str(value))
    doc.add_paragraph()


def add_bullets(doc: Document, items: Iterable[str]) -> None:
    for item in items:
        doc.add_paragraph(item, style="List Bullet")


def add_numbered(doc: Document, items: Iterable[str]) -> None:
    for item in items:
        doc.add_paragraph(item, style="List Number")


def load_font(size: int):
    candidates = [
        "C:/Windows/Fonts/msyh.ttc",
        "C:/Windows/Fonts/simhei.ttf",
        "C:/Windows/Fonts/arial.ttf",
    ]
    for path in candidates:
        if Path(path).exists():
            return ImageFont.truetype(path, size)
    return ImageFont.load_default()


def diagram(path: Path, title: str, nodes: list[tuple[int, int, str, str]], arrows: list[tuple[int, int]]) -> Path:
    im = Image.new("RGB", (1400, 780), "#f3f6fa")
    d = ImageDraw.Draw(im)
    title_font = load_font(38)
    body_font = load_font(24)
    small_font = load_font(18)
    d.text((52, 34), title, fill="#0f172a", font=title_font)
    box_w, box_h = 220, 92
    centers = []
    for x, y, label, note in nodes:
        d.rounded_rectangle((x, y, x + box_w, y + box_h), radius=16, fill="#ffffff", outline="#d8e1ec", width=2)
        d.rectangle((x, y, x + 8, y + box_h), fill="#147d71")
        d.text((x + 26, y + 18), label, fill="#0f172a", font=body_font)
        d.text((x + 26, y + 54), note, fill="#64748b", font=small_font)
        centers.append((x + box_w // 2, y + box_h // 2))
    for a, b in arrows:
        x1, y1 = centers[a]
        x2, y2 = centers[b]
        d.line((x1 + 110, y1, x2 - 110, y2), fill="#64748b", width=4)
        d.polygon([(x2 - 112, y2), (x2 - 132, y2 - 10), (x2 - 132, y2 + 10)], fill="#64748b")
    im.save(path)
    return path


def gantt_diagram(path: Path) -> Path:
    im = Image.new("RGB", (1400, 780), "#f3f6fa")
    d = ImageDraw.Draw(im)
    title_font = load_font(38)
    h_font = load_font(24)
    b_font = load_font(20)
    s_font = load_font(16)
    d.text((52, 36), "项目进度甘特图", fill="#0f172a", font=title_font)
    left, top = 190, 150
    week_w = 130
    for i in range(1, 8):
        x = left + (i - 1) * week_w
        d.rounded_rectangle((x, top, x + week_w - 8, top + 46), radius=10, fill="#ffffff", outline="#d8e1ec")
        d.text((x + 34, top + 12), f"第{i}周", fill="#0f172a", font=s_font)
    rows = [
        ("需求与设计", 1, 2, "#2563eb", "需求分析、架构、数据库、接口草案"),
        ("基础模块", 2, 3, "#147d71", "登录权限、客户、合同起草、前端框架"),
        ("流程闭环", 4, 5, "#16a34a", "分配、会签、定稿、审批、签订"),
        ("结题强化", 6, 7, "#f59e0b", "回退模型、分片上传、测试与演示"),
        ("文档答辩", 6, 7, "#dc2626", "Word 报告、PPT、MP4 演示视频"),
    ]
    for r, (name, start, end, color, note) in enumerate(rows):
        y = 240 + r * 86
        d.text((58, y + 12), name, fill="#0f172a", font=h_font)
        bar_x = left + (start - 1) * week_w
        bar_w = (end - start + 1) * week_w - 18
        d.rounded_rectangle((bar_x, y, bar_x + bar_w, y + 40), radius=16, fill=color)
        d.text((bar_x + 18, y + 9), note, fill="#ffffff", font=s_font)
        d.text((left + 7 * week_w + 10, y + 8), f"交付：{name}", fill="#64748b", font=s_font)
    d.rounded_rectangle((58, 690, 1280, 735), radius=14, fill="#ffffff", outline="#d8e1ec")
    d.text((82, 702), "里程碑：中期后以 PR2 为基准，结题阶段聚焦流程可靠性、附件续传、文档与视频验收。", fill="#0f172a", font=b_font)
    im.save(path)
    return path


def usecase_diagram(path: Path) -> Path:
    im = Image.new("RGB", (1400, 780), "#f3f6fa")
    d = ImageDraw.Draw(im)
    title_font = load_font(38)
    h_font = load_font(23)
    s_font = load_font(17)
    d.text((52, 36), "合同管理系统用例图", fill="#0f172a", font=title_font)
    d.rounded_rectangle((260, 130, 1120, 690), radius=24, fill="#ffffff", outline="#d8e1ec", width=2)
    d.text((290, 150), "ContractSys 系统边界", fill="#147d71", font=h_font)
    actors = [("普通用户", 70, 180), ("起草人", 70, 430), ("流程人员", 1170, 220), ("管理员", 1170, 500)]
    for name, x, y in actors:
        d.ellipse((x + 34, y, x + 74, y + 40), fill="#ffffff", outline="#64748b", width=2)
        d.line((x + 54, y + 40, x + 54, y + 105), fill="#64748b", width=3)
        d.line((x + 20, y + 62, x + 88, y + 62), fill="#64748b", width=3)
        d.line((x + 54, y + 105, x + 25, y + 146), fill="#64748b", width=3)
        d.line((x + 54, y + 105, x + 86, y + 146), fill="#64748b", width=3)
        d.text((x + 8, y + 158), name, fill="#0f172a", font=h_font)
    cases = [
        ("注册登录", 335, 235, "#2563eb"),
        ("客户管理", 560, 235, "#147d71"),
        ("合同起草", 785, 235, "#16a34a"),
        ("附件上传", 335, 390, "#f59e0b"),
        ("会签/审批/签订", 560, 390, "#dc2626"),
        ("打回/恢复", 785, 390, "#0f766e"),
        ("模板管理", 335, 545, "#2563eb"),
        ("查询统计", 560, 545, "#147d71"),
        ("日志审计", 785, 545, "#16a34a"),
    ]
    centers = []
    for label, x, y, color in cases:
        d.ellipse((x, y, x + 170, y + 64), fill="#f8fafc", outline=color, width=3)
        d.text((x + 34, y + 19), label, fill="#0f172a", font=s_font)
        centers.append((x + 85, y + 32))
    actor_points = [(154, 260), (154, 510), (1170, 302), (1170, 580)]
    links = [(0, 0), (1, 1), (1, 2), (1, 3), (2, 4), (2, 5), (3, 6), (3, 7), (3, 8)]
    for actor_idx, case_idx in links:
        x1, y1 = actor_points[actor_idx]
        x2, y2 = centers[case_idx]
        d.line((x1, y1, x2, y2), fill="#94a3b8", width=2)
    im.save(path)
    return path


def workflow_diagram(path: Path) -> Path:
    im = Image.new("RGB", (1400, 780), "#f3f6fa")
    d = ImageDraw.Draw(im)
    title_font = load_font(38)
    h_font = load_font(23)
    s_font = load_font(16)
    d.text((52, 36), "合同主流程与回退流程", fill="#0f172a", font=title_font)

    def arrow(x1, y1, x2, y2, color="#64748b", width=4):
        d.line((x1, y1, x2, y2), fill=color, width=width)
        if abs(x2 - x1) >= abs(y2 - y1):
            direction = 1 if x2 >= x1 else -1
            d.polygon([(x2, y2), (x2 - direction * 18, y2 - 10), (x2 - direction * 18, y2 + 10)], fill=color)
        else:
            direction = 1 if y2 >= y1 else -1
            d.polygon([(x2, y2), (x2 - 10, y2 - direction * 18), (x2 + 10, y2 - direction * 18)], fill=color)

    stages = [
        ("起草", "DRAFT", 80, 185, "#2563eb"),
        ("分配", "ASSIGNED", 325, 185, "#147d71"),
        ("会签", "COUNTERSIGNED", 570, 185, "#f59e0b"),
        ("定稿", "FINALIZED", 815, 185, "#16a34a"),
        ("签订", "SIGNED", 1060, 185, "#0f766e"),
    ]
    centers = []
    for label, status, x, y, color in stages:
        d.rounded_rectangle((x, y, x + 180, y + 92), radius=16, fill="#ffffff", outline="#d8e1ec", width=2)
        d.rectangle((x, y, x + 180, y + 10), fill=color)
        d.text((x + 58, y + 25), label, fill="#0f172a", font=h_font)
        d.text((x + 30, y + 58), status, fill="#64748b", font=s_font)
        centers.append((x + 90, y + 46))
    for i in range(len(centers) - 1):
        x1, y1 = centers[i]
        x2, y2 = centers[i + 1]
        arrow(x1 + 90, y1, x2 - 90, y2)

    d.text((102, 145), "正常流转", fill="#0f172a", font=s_font)

    blocks = [
        ("撤回任务", 80, 440, "#0f766e", "下一环节未完成"),
        ("新轮任务", 380, 440, "#2563eb", "旧轮 SUPERSEDED"),
        ("起草人恢复", 680, 440, "#f59e0b", "currentRound + 1"),
        ("审批/签订打回", 980, 440, "#dc2626", "设置 returnTargetStage"),
    ]
    for label, x, y, color, note in blocks:
        d.rounded_rectangle((x, y, x + 250, y + 92), radius=16, fill="#ffffff", outline=color, width=3)
        d.text((x + 24, y + 18), label, fill="#0f172a", font=h_font)
        d.text((x + 24, y + 56), note, fill="#64748b", font=s_font)

    d.text((102, 400), "回退/撤回泳道", fill="#0f172a", font=s_font)
    # Rollback lane uses horizontal arrows only; vertical markers show which normal stages can enter it.
    arrow(1140, 278, 1140, 440, "#dc2626", 4)
    d.text((1154, 344), "签订前可打回", fill="#dc2626", font=s_font)
    arrow(905, 278, 905, 440, "#dc2626", 4)
    d.text((818, 344), "审批可打回", fill="#dc2626", font=s_font)
    arrow(980, 486, 930, 486, "#f59e0b", 4)
    arrow(680, 486, 630, 486, "#2563eb", 4)
    d.text((690, 545), "恢复后按 returnTargetStage 回到重新定稿或重新会签", fill="#0f172a", font=s_font)
    arrow(205, 440, 205, 278, "#0f766e", 4)
    d.text((95, 548), "撤回仅允许在后续环节未完成时执行", fill="#0f766e", font=s_font)
    d.rounded_rectangle((385, 560, 920, 612), radius=12, fill="#ffffff", outline="#d8e1ec")
    d.text((410, 575), "新轮任务成为当前 PENDING；旧轮未完成任务变为 SUPERSEDED。", fill="#0f172a", font=s_font)

    d.rounded_rectangle((80, 650, 1320, 726), radius=14, fill="#ffffff", outline="#d8e1ec")
    d.text((108, 668), "关键约束：currentRound 标记当前轮；contract_task.round 保留历史轮；PENDING 只代表当前可处理任务。", fill="#0f172a", font=s_font)
    d.text((108, 696), "打回不会覆盖旧意见，恢复会生成新轮任务，旧轮未完成待办以 SUPERSEDED 封存。", fill="#0f172a", font=s_font)
    im.save(path)
    return path


def class_diagram(path: Path) -> Path:
    im = Image.new("RGB", (1400, 780), "#f3f6fa")
    d = ImageDraw.Draw(im)
    title_font = load_font(38)
    h_font = load_font(23)
    s_font = load_font(16)
    d.text((52, 36), "核心类关系图", fill="#0f172a", font=title_font)
    classes = [
        ("Contract", ["id / contractNo", "status / currentRound", "drafter / customer"], 90, 170, "#147d71"),
        ("ContractTask", ["taskType / taskStatus", "assignee / round", "opinion / operatedAt"], 410, 170, "#2563eb"),
        ("Attachment", ["originalName", "storedName", "fileSize / uploader"], 730, 170, "#f59e0b"),
        ("SysUser", ["username / status", "roles", "permissions"], 1050, 170, "#16a34a"),
        ("ContractService", ["assign()", "returnContract()", "resume() / withdrawTask()"], 250, 470, "#dc2626"),
        ("ContractAccessGuard", ["ensureCanView", "ensureCanModify", "getContractForUpdate"], 730, 470, "#0f766e"),
    ]
    centers = {}
    for name, fields, x, y, color in classes:
        d.rounded_rectangle((x, y, x + 250, y + 150), radius=14, fill="#ffffff", outline="#d8e1ec", width=2)
        d.rectangle((x, y, x + 250, y + 38), fill=color)
        d.text((x + 18, y + 8), name, fill="#ffffff", font=h_font)
        for i, field in enumerate(fields):
            d.text((x + 18, y + 54 + i * 28), field, fill="#0f172a", font=s_font)
        centers[name] = (x + 125, y + 75)
    rels = [("Contract", "ContractTask", "1:N 生成任务"), ("Contract", "Attachment", "1:N 附件"), ("SysUser", "ContractTask", "1:N 处理"), ("ContractService", "Contract", "状态机操作"), ("ContractService", "ContractAccessGuard", "权限守卫")]
    for a, b, label in rels:
        x1, y1 = centers[a]
        x2, y2 = centers[b]
        d.line((x1, y1, x2, y2), fill="#64748b", width=3)
        d.text(((x1 + x2) // 2 - 52, (y1 + y2) // 2 - 18), label, fill="#334155", font=s_font)
    im.save(path)
    return path


def sequence_diagram(path: Path) -> Path:
    im = Image.new("RGB", (1400, 780), "#f3f6fa")
    d = ImageDraw.Draw(im)
    title_font = load_font(38)
    h_font = load_font(23)
    s_font = load_font(16)
    d.text((52, 36), "审批时序图", fill="#0f172a", font=title_font)
    actors = [("前端页面", 150), ("Controller", 430), ("Service", 710), ("Repository", 990), ("事件监听", 1220)]
    for name, x in actors:
        d.rounded_rectangle((x - 95, 130, x + 95, 184), radius=14, fill="#ffffff", outline="#d8e1ec", width=2)
        d.text((x - 58, 146), name, fill="#0f172a", font=h_font)
        d.line((x, 184, x, 700), fill="#cbd5e1", width=3)
    messages = [
        (150, 430, 230, "POST /approve"),
        (430, 710, 300, "校验权限与请求"),
        (710, 990, 370, "查询合同并加锁"),
        (990, 710, 440, "返回聚合数据"),
        (710, 990, 510, "写任务/状态历史"),
        (710, 1220, 580, "发布 ContractChangedEvent"),
        (430, 150, 650, "返回最新详情"),
    ]
    for x1, x2, y, label in messages:
        d.line((x1, y, x2, y), fill="#64748b", width=3)
        direction = 1 if x2 > x1 else -1
        d.polygon([(x2, y), (x2 - direction * 16, y - 8), (x2 - direction * 16, y + 8)], fill="#64748b")
        d.text((min(x1, x2) + 18, y - 26), label, fill="#0f172a", font=s_font)
    d.rounded_rectangle((80, 710, 1320, 752), radius=12, fill="#ffffff", outline="#d8e1ec")
    d.text((108, 720), "关键点：权限校验、状态前置条件、任务归属、事务写入和事件驱动缓存/日志解耦。", fill="#0f172a", font=s_font)
    im.save(path)
    return path


def make_diagrams() -> dict[str, Path]:
    return {
        "gantt": gantt_diagram(IMG / "gantt.png"),
        "usecase": usecase_diagram(IMG / "usecase.png"),
        "workflow": workflow_diagram(IMG / "workflow.png"),
        "class": class_diagram(IMG / "class.png"),
        "sequence": sequence_diagram(IMG / "sequence.png"),
    }


def add_picture(doc: Document, path: Path, width: float = 6.25) -> None:
    p = doc.add_paragraph()
    p.alignment = WD_ALIGN_PARAGRAPH.CENTER
    p.add_run().add_picture(str(path), width=Inches(width))


def save_doc(name: str, title: str, builder) -> None:
    doc = Document()
    style_doc(doc, title)
    add_title(doc, title, "ContractSys 合同管理系统结题答辩材料")
    builder(doc)
    while doc.paragraphs and not doc.paragraphs[-1].text.strip():
        p = doc.paragraphs[-1]._element
        p.getparent().remove(p)
    try:
        doc.save(OUT / name)
    except PermissionError as exc:
        print(f"Skipped {name}: {exc}")


def startup(doc: Document) -> None:
    doc.add_heading("1. 项目概述", level=1)
    doc.add_paragraph("本项目建设一个基于 B/S 架构的合同管理系统，覆盖客户维护、合同起草、附件上传、流程分配、会签、定稿、审批、签订、查询统计和操作日志。系统采用 Spring Boot + Vue 3 前后端分离架构，目标是在课程综合实践结题阶段交付一个可运行、可演示、可验证、可追溯的合同业务系统。")
    doc.add_paragraph("启动阶段的管理重点是明确范围边界、角色分工、里程碑计划、验收指标和风险应对。结题阶段在此基础上补齐回退模型、分片续传、模板管理、状态机验证、演示视频和六份 Word 报告，形成完整答辩材料。")
    add_table(doc, ["项", "内容"], [
        ["项目名称", "ContractSys 合同管理系统"],
        ["技术路线", "Vue 3 + Spring Boot 3 + Spring Security + Flyway + Docker Compose"],
        ["Git 基准", FACTS["baseline"]],
        ["当前版本", FACTS["head"]],
        ["中期后变更", FACTS["diff"]],
        ["验证摘要", FACTS["tests"]],
        ["成员说明", FACTS["members"]],
    ])

    doc.add_heading("2. 启动目标与验收口径", level=1)
    add_table(doc, ["目标", "启动阶段定义", "结题验收标准"], [
        ["功能闭环", "覆盖合同从起草到签订的主流程", "主流程可演示，且支持打回、撤回、恢复新轮和历史追踪"],
        ["权限控制", "按管理员、起草人、会签人、审批人、签订人划分职责", "菜单、路由、接口和业务数据均有权限校验"],
        ["数据可追溯", "合同状态、任务处理、日志需要留痕", "状态历史、任务轮次、版本历史、操作日志均可查询"],
        ["附件能力", "支持常见合同附件上传下载", "增加文件魔数校验、分片上传、续传查询和合并入库"],
        ["工程交付", "本地可运行，文档可用于答辩", "提供启动脚本、Docker 编排、测试报告、PPT、MP4 和用户手册"],
    ])

    doc.add_heading("3. 代码规模与组成分析", level=1)
    doc.add_paragraph(f"按源代码统计口径排除 node_modules、target、dist、output、outputs、.git 和 .codegraph 等依赖/构建/交付产物后，项目有效文件共 {CODE_STATS['total_files']} 个，总代码与文档行数 {CODE_STATS['total_lines']} 行。该口径覆盖 backend、frontend、scripts、docs 以及根目录必要配置。")
    add_table(doc, ["组成", "目录/文件", "文件数", "行数", "占比", "说明"], CODE_STATS["areas"])
    add_table(doc, ["语言/类型", "扩展名", "文件数", "行数", "占比", "用途"], CODE_STATS["languages"])

    doc.add_heading("4. 时间进度安排", level=1)
    add_picture(doc, DIAGRAMS["gantt"])
    add_table(doc, ["阶段", "时间", "主要任务", "交付物"], [
        ["需求与设计", "第 1-2 周", "需求分析、架构设计、数据库设计、接口草案", "需求分析报告、设计草案"],
        ["基础模块", "第 3 周", "认证权限、客户管理、合同起草、前端框架", "可运行前后端工程"],
        ["流程闭环", "第 4-5 周", "分配、会签、定稿、审批、签订、日志、统计", "主流程可演示版本"],
        ["结题强化", "第 6-7 周", "回退模型、分片上传、模板、测试、演示视频", "结题文档、PPT、视频"],
    ])
    doc.add_heading("5. 分工安排与贡献比例", level=1)
    add_table(doc, ["角色", "真实姓名", "贡献比例", "主要职责", "对应交付物"], [
        [m["role"], m["name"], m["ratio"], m["tasks"], m["deliverables"]] for m in TEAM_MEMBERS
    ])
    doc.add_paragraph("贡献比例按任务复杂度、代码实现量、测试与文档投入、答辩材料产出综合估算，总计 100%。PPT 中给出的项目负责人、后端开发、前端开发、测试分工在此表中展开，数据库/运维作为支撑职责单列，便于启动报告和关闭报告保持一致。")

    doc.add_page_break()
    doc.add_heading("6. 风险识别与启动应对", level=1)
    add_table(doc, ["风险", "影响", "启动阶段应对", "结题状态"], [
        ["合同状态机复杂", "回退、撤回、多审批人容易导致流程卡死", "先定义状态和任务表，再补集成测试", "通过 currentRound、task.round 和 workflow_sim.py 做回归验证"],
        ["权限边界漂移", "前端可见但后端拒绝，或后端接口越权", "统一权限点并要求接口二次校验", "采用 JWT、RBAC、@RequirePermission 和业务归属校验"],
        ["附件上传中断", "较大文件失败后用户需要重新上传", "普通上传先闭环，后续扩展续传", "结题阶段补充分片会话、已传分片查询和 complete 合并"],
        ["文档与代码脱节", "答辩材料无法解释真实实现", "文档跟随阶段里程碑更新", "六份报告、PPT 和 MP4 均围绕实际功能与测试证据组织"],
    ])


def closing(doc: Document) -> None:
    doc.add_heading("1. 项目关闭结论", level=1)
    doc.add_paragraph("截至结题版本，ContractSys 已完成启动报告中定义的核心目标：合同主流程闭环、权限控制、附件管理、查询统计、日志审计、模板管理、回退恢复和演示部署均已具备可验收材料。项目可通过本地脚本启动，配套 Word 报告、PPT 和 MP4 演示视频用于结题答辩。")
    add_table(doc, ["关闭项", "结论"], [
        ["范围完成度", "核心范围已完成，结题阶段新增的回退模型、分片续传和模板管理也纳入演示范围"],
        ["质量状态", FACTS["tests"]],
        ["代码规模", f"有效统计 {CODE_STATS['total_files']} 个文件、{CODE_STATS['total_lines']} 行，排除依赖、构建产物和交付输出目录"],
        ["交付物", "六份 Word 报告、结题 PPTX、MP4 演示视频、启动/停止脚本、Docker Compose、源码和测试记录"],
        ["成员信息", "已按当前 PPT 第 13 页团队页补充真实姓名：赵杰雄、谷友瑄、王凯、项重善、张浩然"],
    ])

    doc.add_heading("2. 已实现功能", level=1)
    add_table(doc, ["模块", "实现内容"], [
        ["认证权限", "注册、登录、JWT 鉴权、角色权限、方法级授权、当前用户注入"],
        ["客户管理", "客户增删改查、引用检查、操作日志记录"],
        ["合同管理", "起草、修改、模板起草、附件上传下载、版本历史、流程时间线"],
        ["合同流程", "分配、会签、定稿、审批、签订、拒绝重提、打回、撤回、恢复新轮"],
        ["查询统计", "高级查询、状态统计、月度统计、CSV 导出"],
        ["系统支撑", "模板管理、日志查询、Docker 启动、演示数据、自动化测试"],
    ])
    doc.add_heading("3. 创新点", level=1)
    add_table(doc, ["创新类别", "原需求描述", "本系统实现", "答辩可强调点"], [
        ["认证与授权", "登录后按角色进入对应页面", "JWT + Spring Security + RBAC 权限点 + @RequirePermission 方法级授权", "从页面角色控制升级为接口级安全边界；前端隐藏只是体验，后端才是最终校验"],
        ["流程状态机", "起草、会签、定稿、审批、签订的单向流程", "Contract.status + ContractTask + StateHistory + currentRound 多轮回退模型", "支持撤回任务、撤回合同、打回到起草/定稿、恢复新轮；历史意见不覆盖"],
        ["附件能力", "起草时上传 doc/jpg/png 等附件", "普通上传 + 文件魔数校验 + 分片上传会话 + 已传分片查询 + 合并入库", "具备中断后续传能力，避免大文件失败后从零开始"],
        ["审计追踪", "日志记录、查阅、备份、导出", "OperationLogEvent 事件记录 + 状态历史 + 版本历史 + CSV 转义防注入", "谁在何时对哪个合同做了什么可以追踪，导出也考虑公式注入风险"],
        ["工程验证", "未明确要求自动化验证", "JUnit 集成测试、Playwright 演示脚本、workflow_sim.py BFS 状态机预演", "不仅能演示 happy path，还能机器化验证回退路径不会卡死"],
        ["部署演示", "B/S + MySQL", "H2 测试、PostgreSQL/Redis Docker、MySQL profile、start-demo.ps1 一键启动", "从课程原型提升到可重复部署、可重复验收的工程交付"],
    ])
    doc.add_heading("4. 对照原作业文档的功能扩展", level=1)
    add_bullets(doc, [
        "原作业的数据字典只有合同流程和合同状态两类记录；本系统拆分为任务表、状态历史、版本历史和操作日志，降低状态查询和历史追溯的耦合。",
        "原作业只要求按合同编号、名称模糊查询；本系统增加客户、起草人、状态、日期范围、状态分布和月度统计。",
        "原作业只要求管理员分配人员；本系统在分配时按权限过滤候选人，避免把审批任务派给无审批权限的用户。",
        "原作业未要求模板管理；本系统增加模板上传、启停、下载和角色可见性，支持起草阶段复用标准模板。",
        "原作业未要求回退模型；本系统补齐真实业务中常见的撤回、打回、多轮修改和历史保留。",
    ])
    doc.add_heading("5. 代码量与组成分析", level=1)
    add_table(doc, ["指标", "结果"], [
        ["总代码量", f"{CODE_STATS['total_lines']} 行 / {CODE_STATS['total_files']} 个有效文件"],
        ["统计口径", "排除 node_modules、target、dist、output、outputs、.git、.codegraph 等依赖、构建和交付产物"],
        ["中期后 Git 变更", FACTS["diff"]],
        ["自动化测试", FACTS["tests"]],
        ["演示脚本", "Playwright 录制脚本输出 MP4"],
        ["部署", "start-demo.ps1 / stop-demo.ps1 / Docker Compose"],
    ])
    add_table(doc, ["组成", "目录/文件", "文件数", "行数", "占比", "关闭评价"], CODE_STATS["areas"])
    doc.add_page_break()
    add_table(doc, ["语言/类型", "扩展名", "文件数", "行数", "占比", "主要价值"], CODE_STATS["languages"])

    doc.add_page_break()
    doc.add_heading("6. 任务分配与贡献比例", level=1)
    add_table(doc, ["角色", "真实姓名", "贡献比例", "完成任务", "主要证据"], [
        [m["role"], m["name"], m["ratio"], m["tasks"], m["deliverables"]] for m in TEAM_MEMBERS
    ])
    doc.add_paragraph("以上比例与 PPT 中的团队页保持同一职责口径，并补充关闭报告需要的贡献权重。姓名按当前 PPT 第 13 页团队页解析结果填写，报告、PPT 和答辩口径保持一致。")

    doc.add_heading("7. 交付清单与归档建议", level=1)
    add_table(doc, ["类别", "文件/目录", "说明"], [
        ["报告", "01-启动报告.docx 至 06-用户使用手册.docx", "项目启动、关闭、需求、设计、测试和用户操作说明"],
        ["答辩", "合同管理系统结题答辩.pptx", "结题展示主材料，含功能、创新、验证、分工和总结"],
        ["视频", "contractsys-final-demo.mp4", "演示系统启动、登录、流程、回退、附件和日志等关键路径"],
        ["源码", "backend / frontend / scripts", "后端、前端、脚本和验证工具"],
        ["部署", "docker-compose.yml / scripts/start-demo.ps1 / scripts/stop-demo.ps1", "本地和容器化运行入口"],
    ])

    doc.add_heading("8. 心得体会", level=1)
    for m in TEAM_MEMBERS:
        doc.add_heading(f"{m['role']}（{m['name']}）", level=2)
        doc.add_paragraph(f"{m['role']}通过本项目理解了合同管理从需求到交付的完整链路。结题阶段的重点不只是补功能，还包括把流程边界、权限边界、测试证据和演示材料串成可验收的闭环。该角色承担的核心内容为：{m['tasks']}。")


def requirements(doc: Document) -> None:
    doc.add_heading("1. 需求背景与范围", level=1)
    doc.add_paragraph("合同管理系统面向企业合同从起草到签订归档的全过程管理。原作业文档要求系统支持客户资料维护、合同起草、会签、审批、签订、查询统计、日志和备份等功能；结题版本在此基础上补充了 JWT/RBAC 权限、模板管理、附件分片续传、版本历史、流程时间线、回退模型和自动化验证，形成可演示、可追踪、可恢复的完整业务闭环。")
    add_table(doc, ["需求来源", "原始要求", "结题实现范围"], [
        ["客户管理", "客户信息录入、修改、查询、删除", "客户 CRUD、被合同引用时禁止删除、操作日志记录"],
        ["合同起草", "起草合同并上传附件", "客户关联、合同编号、模板参考、普通附件和分片续传附件"],
        ["合同会签", "多个人员会签并填写意见", "会签待办、意见留痕、全部会签完成后进入定稿"],
        ["合同审批", "审批合同，通过或退回", "审批通过、审批拒绝、打回到重新定稿/重新起草"],
        ["合同签订", "签订后合同归档", "签订日期、签订信息、终态保护、附件/版本/日志可查"],
        ["查询统计", "合同查询、统计、报表", "多条件查询、状态分布、月度统计、合同 CSV 导出"],
        ["系统管理", "用户、日志、备份", "用户/角色/权限、操作日志查询与导出、Docker/脚本化部署"],
    ])

    doc.add_heading("2. 用户角色与权限需求", level=1)
    add_table(doc, ["角色", "主要目标", "核心权限", "典型操作"], [
        ["系统管理员", "维护账号、角色、权限和全局数据", "user:manage、role:manage、permission:manage、log:view", "创建用户、分配角色、查看操作日志"],
        ["合同管理员", "组织合同流转并管理模板", "contract:assign、contract:view、contract:query", "上传模板、分配会签/审批/签订人员、查看统计"],
        ["起草人", "维护客户并起草/修改合同", "customer:manage、contract:create、contract:update", "起草合同、上传附件、处理打回、恢复流程"],
        ["会签人", "对合同条款提出专业意见", "contract:countersign、contract:view", "提交会签意见、打回到重新起草"],
        ["审批人", "对定稿合同进行审批决策", "contract:approve、contract:view", "审批通过、审批拒绝、打回到重新定稿/起草"],
        ["签订人", "完成合同签订归档", "contract:sign、contract:view", "录入签订日期和签订说明、签订前打回"],
        ["审计/查询人员", "检索合同和导出报表", "contract:query、log:view", "多条件查询、导出合同 CSV、导出日志 CSV"],
    ])

    doc.add_heading("3. 功能性需求", level=1)
    add_picture(doc, DIAGRAMS["usecase"])
    doc.add_paragraph("用例图中的系统边界覆盖合同生命周期和系统支撑能力。为避免图过空，图中不仅列出主流程用例，也列出模板、附件、查询统计、日志审计和回退恢复等结题新增用例。")
    add_table(doc, ["参与者", "需求"], [
        ["普通用户", "注册、登录、查看个人信息、按权限访问菜单"],
        ["起草人", "维护客户、起草合同、上传附件、定稿、处理打回合同"],
        ["会签人", "查看待办、提交会签意见、打回到起草"],
        ["审批人", "提交审批意见、审批通过或打回到定稿/起草"],
        ["签订人", "录入签订信息，完成合同归档"],
        ["管理员", "管理用户、角色、权限、模板、日志和统计"],
    ])

    doc.add_heading("4. 主要用例规约", level=1)
    add_table(doc, ["用例", "前置条件", "主流程", "异常/扩展"], [
        ["登录与权限加载", "用户已存在且状态正常", "输入账号密码；后端校验；返回 JWT 和用户信息；前端加载权限菜单", "密码错误返回 401；无权限菜单不显示；接口越权返回 403"],
        ["客户维护", "用户具备 customer:manage", "新增客户资料；编辑联系方式、银行信息和备注；列表查询", "客户被合同引用时禁止删除，防止合同关联数据断裂"],
        ["模板管理", "合同管理员登录", "上传模板文件；填写说明；设置可见角色；启停模板；下载模板", "文件扩展名或魔数不匹配时拒绝；停用模板对起草人不可见"],
        ["起草合同", "存在客户，用户具备 contract:create", "填写合同字段；选择附件；提交后生成合同编号、版本和分配待办", "结束日期早于开始日期时报错；附件不合法时报错"],
        ["分片上传附件", "合同为草稿，操作者为起草人且具备 contract:update", "创建上传会话；上传分片；查询已传分片；合并完成；附件列表刷新", "网络中断后只补传缺失分片；合并失败不生成附件记录"],
        ["分配流程人员", "合同处于 DRAFT，存在分配待办", "选择会签人、审批人、签订人；提交分配；生成会签待办", "签订人不能是起草人；候选人必须具备对应权限"],
        ["会签与定稿", "合同已分配，会签任务待处理", "会签人提交意见；全部会签完成；起草人定稿并产生版本", "会签人可打回重新起草；定稿人必须是起草人"],
        ["审批与打回", "合同已定稿，审批任务待处理", "审批人通过、拒绝或打回；通过后进入签订；打回后进入恢复流程", "审批拒绝为终止状态；打回目标可为重新定稿或重新起草"],
        ["恢复新轮流程", "合同处于 RETURNED 或可恢复草稿，操作者为起草人", "查看打回原因；修改合同；点击恢复；系统生成新轮任务", "非起草人不能恢复；旧轮任务必须保留且不可误处理"],
        ["签订归档", "合同审批通过，签订任务待处理", "签订人填写签订日期和签订说明；提交后状态为 SIGNED", "签订前可打回；签订后不允许继续编辑和恢复"],
        ["查询统计导出", "用户具备 contract:query", "按关键字、状态、客户和日期筛选；查看统计；导出 CSV", "导出字段需要转义，防止逗号、换行和公式注入"],
        ["日志审计", "用户具备 log:view", "按模块、关键字、时间筛选日志；导出日志 CSV", "敏感操作应记录操作人、对象、动作和内容"],
    ])

    doc.add_heading("5. 业务规则与约束", level=1)
    add_table(doc, ["规则类别", "规则描述", "原因"], [
        ["状态前置", "每个流程动作只能在允许状态执行，例如只有 FINALIZED 可审批、APPROVED 可签订", "避免合同状态乱跳"],
        ["任务归属", "只有当前任务处理人可以完成该任务", "防止其他用户代办或越权处理"],
        ["轮次隔离", "currentRound 表示当前轮，ContractTask.round 保存历史轮", "支持回退后保留旧意见并生成新任务"],
        ["当前待办", "PENDING 只代表当前可处理任务，旧轮未完成任务用 SUPERSEDED 封存", "避免待办列表出现重复或过期待办"],
        ["人员约束", "签订人员不能为起草人；分配候选人必须具备对应权限", "降低职责冲突和错误分配"],
        ["附件约束", "附件最大 10MB，限制文件类型并校验文件魔数", "满足作业要求同时降低文件安全风险"],
        ["终态保护", "SIGNED、REJECTED、CANCELLED 等终态不允许继续修改流程", "保证归档数据稳定"],
        ["审计留痕", "关键操作必须写入状态历史、版本历史或操作日志", "满足结题验收和责任追踪"],
    ])

    doc.add_heading("6. 数据需求", level=1)
    add_table(doc, ["数据对象", "主要字段", "数据要求"], [
        ["用户", "用户名、密码、状态、角色", "用户名唯一，密码加密存储，禁用用户不能登录"],
        ["角色权限", "角色编码、权限编码、URL/说明", "支持多角色、多权限组合，权限点与后端注解一致"],
        ["客户", "名称、电话、地址、开户行、账号、备注", "客户名称用于合同关联和查询，引用后删除受限"],
        ["合同", "编号、名称、客户、起草人、状态、轮次、合同正文", "合同编号自动生成，状态由流程服务维护"],
        ["任务", "类型、状态、处理人、轮次、意见、操作时间", "任务记录必须可按合同和轮次查询"],
        ["附件", "原始文件名、存储文件名、大小、上传人、时间", "物理文件与数据库记录一致，删除需事务后处理"],
        ["模板", "名称、说明、文件、启用状态、可见角色", "停用模板不展示给普通起草人"],
        ["日志", "操作人、模块、动作、对象、内容、时间", "支持筛选、分页和 CSV 导出"],
    ])

    doc.add_heading("7. 非功能性需求", level=1)
    add_table(doc, ["类别", "要求", "实现方式"], [
        ["安全性", "未登录和无权限必须被拦截", "JWT、401/403、RBAC 权限点"],
        ["可追踪性", "合同变更与操作可追溯", "状态历史、任务表、操作日志"],
        ["一致性", "流程状态不可乱跳", "事务、状态前置校验、任务归属校验"],
        ["可维护性", "模块边界清晰", "Controller-Service-Repository 分层"],
        ["部署性", "本地和容器均可启动", "H2、PostgreSQL、MySQL profile、Docker"],
        ["可用性", "常用流程入口清晰，待办和合同详情可互相跳转", "工作台、我的待办、合同详情页签、状态徽标"],
        ["可恢复性", "打回和撤回后能继续推进，不丢失历史意见", "回退模型、轮次隔离、保留分配人员"],
        ["可测试性", "主流程和边界可自动化验证", "JUnit、MockMvc、workflow_sim.py、Playwright 脚本"],
        ["兼容性", "开发、测试、部署数据库环境可切换", "Flyway 多数据库迁移脚本和 profile 配置"],
    ])

    doc.add_heading("8. 验收标准", level=1)
    add_table(doc, ["验收项", "通过标准"], [
        ["登录权限", "管理员、起草人、会签人、审批人、签订人登录后菜单和接口权限符合角色"],
        ["主流程", "合同可从起草、分配、会签、定稿、审批到签订完整闭环"],
        ["回退模型", "审批打回后可恢复新轮，旧轮任务和意见保留，当前轮清晰展示"],
        ["附件续传", "分片上传可完成合并，已上传分片可查询并补传缺失部分"],
        ["模板管理", "模板可上传、下载、启停并按角色可见"],
        ["查询统计", "多条件查询、状态统计、月度统计和 CSV 导出可用"],
        ["日志审计", "合同、客户、用户等关键操作可在日志中查询和导出"],
        ["测试证据", "后端测试、状态机预演、Playwright 演示截图/视频和文档渲染结果可作为验收材料"],
    ])


def design(doc: Document) -> None:
    doc.add_heading("1. 总体架构", level=1)
    doc.add_paragraph("系统采用 Vue 3 前端、Spring Boot 3 后端和关系型数据库。前端负责路由、表单和流程可视化；后端负责认证授权、业务状态机、文件存储、日志审计和统计查询。")
    add_table(doc, ["层次", "主要组件", "职责", "设计取舍"], [
        ["表示层", "Vue 3、Vue Router、Pinia、Axios", "页面路由、权限菜单、表单校验、状态徽标、流程页签和上传进度", "前端只做体验层权限隐藏，最终授权由后端接口控制"],
        ["接口层", "Controller、CurrentUserArgumentResolver、统一 ApiResponse", "接收请求、参数校验、当前用户注入、统一响应结构", "接口路径按资源组织，便于 Swagger 和 Playwright 自动化演示"],
        ["业务层", "ContractService、ContractQueryService、AttachmentService、TemplateService", "合同状态机、查询统计、附件分片、模板管理、版本历史", "写操作集中在服务层，避免控制器拼装状态流转"],
        ["安全层", "Spring Security、JWT、@RequirePermission、RBAC", "登录认证、接口鉴权、方法级权限、401/403 分离", "页面权限和接口权限双层防护，防止绕过前端直接调用接口"],
        ["数据层", "JPA Repository、Flyway、H2/PostgreSQL/MySQL", "实体持久化、迁移脚本、测试和部署数据库适配", "开发测试用 H2，演示/部署可切 PostgreSQL，保留 MySQL profile"],
        ["支撑层", "OperationLogEvent、ContractChangedEvent、Redis 可选缓存", "操作审计、统计缓存失效、日志导出", "通过事件解耦业务写入和审计/缓存副作用"],
    ])
    add_picture(doc, DIAGRAMS["workflow"])
    doc.add_heading("2. 领域模型与数据设计", level=1)
    doc.add_paragraph("系统的数据模型从原作业文档的合同、客户、用户、角色扩展为“合同聚合 + 流程任务 + 状态历史 + 版本历史 + 附件 + 模板 + 操作日志”。这样做的原因是合同流程存在多角色、多轮次、回退和审计需求，单个状态字段无法解释合同为什么到达某个状态。")
    add_table(doc, ["实体/表", "关键字段", "主要关系", "用途"], [
        ["Contract", "contractNo、status、currentRound、returnTargetStage、version", "N:1 Customer、N:1 drafter", "合同主聚合，保存当前状态、轮次和业务正文"],
        ["ContractTask", "taskType、taskStatus、assignee、round、opinion、operatedAt", "N:1 Contract、N:1 SysUser", "表达分配、会签、定稿、审批、签订、修改等待办和历史任务"],
        ["ContractStateHistory", "fromStatus、toStatus、operator、remark、createdAt", "N:1 Contract", "记录状态跳转，支撑流程时间线"],
        ["ContractVersion", "versionNo、name、content、operator、remark", "N:1 Contract", "记录起草、修改、定稿等文本版本"],
        ["Attachment", "originalName、storedName、fileSize、uploader、uploadedAt", "N:1 Contract", "保存合同附件元数据，物理文件由 FileStorageService 管理"],
        ["ContractTemplate", "name、enabled、visibleRoles、storedName", "独立模板资源", "支持模板上传、下载、启停和角色可见性"],
        ["OperationLog", "operator、module、action、targetType、content", "事件监听写入", "审计用户操作并支持 CSV 导出"],
        ["SysUser/Role/Permission", "username、roleCode、permissionCode", "用户-角色-权限多对多", "支撑 RBAC 和方法级授权"],
    ])
    doc.add_heading("3. 核心类图", level=1)
    add_picture(doc, DIAGRAMS["class"])
    doc.add_paragraph("核心类图展示了合同聚合与任务、附件、用户和服务层之间的关系。ContractService 负责所有会改变流程状态的写操作；ContractAccessGuard 负责查看、修改和加锁入口，避免每个方法重复权限与状态检查；ContractQueryService 负责详情、时间线、版本和查询统计，降低写服务复杂度。")
    doc.add_heading("4. 关键流程设计", level=1)
    doc.add_heading("4.1 审批与打回时序", level=2)
    add_picture(doc, DIAGRAMS["sequence"])
    add_numbered(doc, [
        "前端在合同详情或我的待办中提交审批请求，Axios 自动带上 JWT。",
        "JwtAuthenticationFilter 解析 token，CurrentUserArgumentResolver 注入当前用户。",
        "Controller 先经过 @RequirePermission，只有具备 contract:approve 的用户可进入审批服务。",
        "ContractService 通过 getContractForUpdate 获取加锁聚合，校验合同当前状态、任务归属和任务轮次。",
        "审批通过时完成当前审批任务；所有审批任务完成后进入 APPROVED 并生成签订任务。",
        "审批打回时设置 returnTargetStage，完成/封存相关任务，合同进入 RETURNED 或 DRAFT 可恢复状态。",
        "状态变更同步写入 StateHistory，并发布 ContractChangedEvent / OperationLogEvent。",
    ])
    doc.add_heading("4.2 分片上传流程", level=2)
    add_table(doc, ["步骤", "前端行为", "后端行为", "失败恢复"], [
        ["创建会话", "根据文件大小计算 totalChunks，保存 uploadId 到 localStorage", "校验合同状态、上传人和文件元数据，创建 manifest", "刷新页面后可用 uploadId 查询会话"],
        ["上传分片", "按 1MB 切片逐个 PUT，更新进度百分比", "保存 index.part，并记录已上传分片", "已完成分片不会重复上传"],
        ["查询会话", "再次选择同文件时读取本地 uploadId", "返回 uploadedChunks、chunkSize、totalChunks", "只补传缺失分片"],
        ["合并完成", "调用 complete 后刷新附件列表", "按顺序合并分片，做文件魔数校验，写 Attachment", "合并失败不生成正式附件"],
    ])
    doc.add_heading("4.3 回退模型状态设计", level=2)
    add_table(doc, ["场景", "状态变化", "任务变化", "页面观察点"], [
        ["审批打回到重新定稿", "FINALIZED -> RETURNED", "审批任务记录 REJECTED/打回；生成起草人 REVISE 待办", "回退目标显示重新定稿，第 1 轮任务保留"],
        ["起草人恢复", "RETURNED -> COUNTERSIGNED 或 DRAFT -> ASSIGNED", "currentRound + 1；旧轮未完成任务 SUPERSEDED；新轮生成当前待办", "回退模型页出现第 2 轮当前轮和第 1 轮历史轮"],
        ["撤回合同", "ASSIGNED -> DRAFT", "保留已分配人员，后续恢复时跳过重新分配", "基础信息显示保留分配提示"],
        ["撤回任务", "回退到上一可处理环节", "最近已处理任务重新变为 PENDING，后续未完成任务关闭", "我的待办和按钮状态同步更新"],
        ["签订完成", "APPROVED -> SIGNED", "签订任务 DONE，流程终止", "只允许查看，不再展示编辑/恢复按钮"],
    ])
    doc.add_heading("5. 权限与安全设计", level=1)
    add_table(doc, ["安全点", "实现", "说明"], [
        ["登录认证", "AuthController + JwtTokenProvider + JwtAuthenticationFilter", "登录成功返回 JWT，后续请求通过 Authorization Bearer token 认证"],
        ["接口授权", "@RequirePermission + RequirePermissionAuthorizationManager", "每个业务接口声明权限点，未授权返回 403"],
        ["当前用户", "@CurrentUser + 参数解析器", "业务服务拿到真实用户对象，避免客户端传 userId 冒充"],
        ["角色权限", "SysUser、SysRole、SysPermission 多对多", "管理员可管理用户、角色、权限，演示账号按角色隔离"],
        ["文件安全", "扩展名、大小、文件魔数校验", "防止伪装 PDF/DOCX/图片进入附件或模板库"],
        ["导出安全", "CsvEscaper", "逗号、引号、换行转义，并防护 Excel 公式注入"],
    ])
    doc.add_heading("6. 界面样式", level=1)
    for img in ["11-dashboard-admin.png", "13-contract-timeline.png", "14-contract-versions.png", "15-contract-attachments.png", "17-operation-logs.png"]:
        src = ROOT / "outputs" / "midterm-ppt" / "assets" / img
        if src.exists():
            add_picture(doc, src, 5.8)
    doc.add_paragraph("界面延续中期版本的深色侧栏、青绿色状态色和卡片式内容区。结题阶段重点新增合同详情页签：流程任务、流程时间线、版本历史、回退模型和附件。回退模型页面将当前轮、历史轮、回退目标和保留分配集中展示，便于验收人员直接判断多轮流程是否正确。")
    doc.add_heading("7. 关键设计创新说明", level=1)
    add_table(doc, ["设计点", "结构", "原因"], [
        ["多轮回退", "contract.currentRound + contract_task.round + SUPERSEDED", "避免复用旧任务导致意见丢失；每轮任务独立可追踪"],
        ["任务激活", "PENDING 只表示当前可处理任务", "待办数、红点、按钮可用性口径统一，减少“看得到但不能点”的问题"],
        ["上传会话", "manifest.properties + index.part + complete 合并", "浏览器中断后可查询已传分片并继续上传"],
        ["事件解耦", "ContractChangedEvent / OperationLogEvent", "合同服务只发布业务事件，日志和统计缓存由监听器处理"],
        ["状态机预演", "Python BFS 模型镜像 ContractService 语义", "对复杂回退路径做穷举，发现并修复人工测试不容易覆盖的卡死路径"],
    ])
    doc.add_heading("8. 可维护性设计", level=1)
    add_bullets(doc, [
        "数据库迁移按 H2、PostgreSQL、MySQL 分目录维护，避免测试库和部署库结构漂移。",
        "合同查询、统计、导出、附件、模板分别拆分服务，避免 ContractService 继续膨胀。",
        "前端常量 contract.js 统一状态标签，StatusBadge 和 PipelineStepper 复用状态展示。",
        "演示脚本通过 API 预填充状态，再用 Playwright 截图/录制，减少手工演示的不确定性。",
        "测试报告和使用手册引用同一套功能路径：登录、模板、起草、附件、分配、回退、时间线、版本、查询和日志。",
    ])


def testing(doc: Document) -> None:
    doc.add_heading("1. 测试目标与环境", level=1)
    doc.add_paragraph("测试报告结合自动化测试、脚本冒烟、Playwright 演示录制和人工迭代验收记录编写。测试目标不是只验证 happy path，而是覆盖权限边界、流程状态机、附件安全、导出安全和演示可重复性。")
    add_table(doc, ["环境项", "配置"], [
        ["后端单元/集成测试", "Spring Boot Test + JUnit 5 + MockMvc + H2 functional-test profile"],
        ["前端构建校验", "Vue 3 + Vite build，校验路由、组件和打包依赖"],
        ["演示环境", ".\\scripts\\start-demo.ps1 启动后端 8080 和前端 5173"],
        ["浏览器自动化", "Playwright 预填充数据、截图和 MP4 录制"],
        ["部署冒烟", "docker-smoke.ps1 检查前端、后端、Swagger、代理和登录接口"],
        ["状态机预演", "workflow_sim.py BFS 枚举流程状态，检查死锁、全局卡死和重复待办"],
    ])

    doc.add_heading("2. 自动化测试覆盖", level=1)
    add_table(doc, ["测试类/脚本", "覆盖内容", "结题说明"], [
        ["PermissionIntegrationTest", "登录、角色、权限、未授权访问、管理员保护、权限矩阵", "证明 JWT + RBAC + @RequirePermission 的接口级边界"],
        ["BoundaryIntegrationTest", "异常输入、越权、状态前置条件、删除/取消边界、附件限制", "覆盖人工演示中容易漏掉的错误分支"],
        ["ContractWorkflowTraversalIntegrationTest", "起草、分配、会签、定稿、审批、签订、打回、恢复、撤回", "验证主流程和回退模型在多轮任务下可闭环"],
        ["ContractQueryServiceTest", "合同列表、详情、查询过滤、可见性", "验证查询统计入口不会越权展示数据"],
        ["StatisticsAuditExportIntegrationTest", "状态统计、月度统计、日志记录、合同/日志 CSV 导出", "覆盖结题新增的查询统计和审计导出"],
        ["CsvEscaperTest", "CSV 逗号、引号、换行、公式注入前缀", "验证导出文件不会被 Excel 公式注入利用"],
        ["CacheSerializationTest", "统计缓存对象序列化与反序列化", "验证 Redis 可选缓存不会破坏统计结构"],
        ["DemoDataInitializerIntegrationTest", "演示数据填充和普通启动清理", "保证演示环境可重复启动，不污染普通运行"],
        ["smoke-pr2.ps1", "认证、权限、客户、合同主流程基础冒烟", "中期后用于快速确认核心 API 可用"],
        ["docker-smoke.ps1", "Docker 前后端连通、Swagger、代理和登录", "验证部署环境不是只在本机 Maven 下可运行"],
        ["final-demo-record.mjs / manual-screenshots.mjs", "Playwright 录制和截图：模板、起草、附件、回退、统计、日志", "用于结题答辩视频和手册插图，确保演示数据可重复生成"],
    ])
    doc.add_heading("3. 功能模块测试用例", level=1)
    add_table(doc, ["编号", "模块", "测试步骤", "预期结果"], [
        ["TC-01", "登录认证", "未登录访问 /contracts，再登录 admin/123456", "未登录返回 401 或跳转登录；登录成功后进入工作台"],
        ["TC-02", "权限控制", "普通操作员直接访问用户管理或调用管理接口", "前端无菜单，后端返回 403"],
        ["TC-03", "客户管理", "新增客户、编辑客户、尝试删除已被合同引用客户", "新增编辑成功；被引用客户删除被阻止"],
        ["TC-04", "模板管理", "管理员上传 PDF 模板、设置 ROLE_OPERATOR 可见、停用后用起草人查看", "上传成功；停用后起草人不可见；启用后恢复可见"],
        ["TC-05", "合同起草", "起草合同并选择客户、日期、内容和附件", "生成合同编号、V1 版本、状态历史和分配待办"],
        ["TC-06", "分片续传", "创建上传会话，上传部分分片后查询 uploadedChunks，再补传并 complete", "缺失分片可续传；合并后附件列表出现文件"],
        ["TC-07", "文件安全", "上传扩展名为 PDF 但内容不以 %PDF 开头的文件", "后端拒绝并提示文件内容与扩展名不匹配"],
        ["TC-08", "分配人员", "合同管理员分配会签、审批、签订人员", "候选人按权限过滤；提交后合同进入 ASSIGNED"],
        ["TC-09", "会签定稿", "会签人提交意见，起草人提交定稿", "会签任务 DONE，合同进入 COUNTERSIGNED；定稿后进入 FINALIZED 并记录版本"],
        ["TC-10", "审批打回", "审批人选择打回到重新定稿并填写原因", "合同进入 RETURNED，returnTargetStage=FINALIZE，起草人收到修改待办"],
        ["TC-11", "恢复新轮", "起草人处理打回后点击恢复流程", "currentRound 递增；旧轮保留；新轮生成当前任务"],
        ["TC-12", "签订完成", "第二轮定稿、审批通过、签订人提交签订信息", "合同状态 SIGNED；不再展示编辑、恢复、撤回按钮"],
        ["TC-13", "撤回任务", "在后续环节未完成前撤回最近已处理任务", "最近任务回到 PENDING，后续任务关闭或重新生成"],
        ["TC-14", "查询导出", "按状态、客户、日期范围查询并导出合同 CSV", "列表过滤正确，CSV 字段转义正确"],
        ["TC-15", "日志审计", "完成合同流程后按 CONTRACT 模块筛选日志并导出", "日志记录操作人、动作、对象和状态变化，CSV 可下载"],
    ])
    doc.add_heading("4. 回退模型专项测试", level=1)
    add_table(doc, ["路径", "输入状态", "操作", "检查点"], [
        ["审批打回到定稿", "FINALIZED + APPROVAL PENDING", "审批人 return target=FINALIZE", "RETURNED；审批意见保留；起草人 REVISE 待办生成"],
        ["审批打回到起草", "FINALIZED + APPROVAL PENDING", "审批人 return target=DRAFT", "DRAFT 或 RETURNED 可恢复草稿；原分配人员可保留"],
        ["会签打回", "ASSIGNED + COUNTERSIGN PENDING", "会签人 return target=DRAFT", "回到起草处理；会签意见进入任务历史"],
        ["恢复定稿", "RETURNED + returnTargetStage=FINALIZE", "起草人 resume", "currentRound+1；生成 FINALIZE 待办；旧轮非当前待办封存"],
        ["恢复会签", "DRAFT + returnTargetStage=DRAFT", "起草人 resume", "跳过重新分配，使用保留人员生成会签任务"],
        ["撤回任务", "当前轮已有 DONE 且下一环节未完成", "处理人 withdrawTask", "最近任务恢复可处理，后续任务不会残留重复 PENDING"],
        ["终态保护", "SIGNED 或 CANCELLED", "继续修改、恢复、撤回", "接口拒绝，页面不展示操作按钮"],
    ])
    doc.add_heading("5. 人工迭代与缺陷修复记录", level=1)
    add_table(doc, ["迭代发现", "原因", "修正方式", "回归方式"], [
        ["回退模型页信息不足", "只展示任务列表，无法直观看到当前轮和历史轮", "增加 currentRound、returnTargetStage、保留分配、当前轮/历史轮标识", "Playwright 截图检查第 1/2 轮展示"],
        ["回退流程容易出现旧待办残留", "旧轮 PENDING 和新轮 PENDING 口径混在一起", "引入 task.round 和 SUPERSEDED，PENDING 只代表当前可处理任务", "workflow_sim.py 与流程集成测试回归"],
        ["分配候选人可能不具备对应权限", "按用户列表选择，没有按任务权限过滤", "assignable 接口按 permission 参数过滤候选人", "权限集成测试和分配页人工检查"],
        ["附件只支持一次性上传", "大文件中断后必须重传", "新增 chunk-session、uploadedChunks、complete 合并流程", "手册截图脚本上传 1MB 以上 PDF 并检查附件列表"],
        ["伪造扩展名文件可进入上传流程", "只看文件名扩展名不够", "FileStorageService 增加文件魔数校验", "上传假 PDF 期望返回错误"],
        ["CSV 导出存在公式注入风险", "以 =、+、-、@ 开头字段在 Excel 中可能执行", "CsvEscaper 为危险前缀加单引号并处理引号/换行", "CsvEscaperTest 覆盖公式前缀"],
        ["演示环境数据不稳定", "手工操作容易漏步骤或状态不可复现", "Playwright/API 脚本自动创建用户、客户、合同和流程状态", "生成 MP4 与 14 张手册截图"],
        ["DOCX 渲染出现尾页孤行", "手册常见问题表格跨页只剩一行", "压缩常见问题表，删除重复端口说明", "LibreOffice 导出 PDF 后 pdftoppm 生成 15 页检查"],
    ])
    doc.add_heading("6. 自动化验证结果", level=1)
    add_bullets(doc, [
        FACTS["tests"],
        "Playwright 演示脚本覆盖登录、模板、合同起草、分片附件、分配、审批打回、恢复新轮、签订、时间线、版本、查询统计和日志页面。",
        "manual-screenshots.mjs 通过 API 构造确定状态，再进入前端页面截图，避免人工录制时状态漂移。",
        "docker-smoke.ps1 验证 Docker 前端、后端、Swagger、代理和登录接口。",
        "文档渲染验证中，用户手册可由 LibreOffice 导出 PDF，并由 pdftoppm 渲染为 15 页 PNG 进行视觉检查。",
    ])
    doc.add_heading("7. 残余风险与后续测试建议", level=1)
    add_table(doc, ["风险", "当前控制", "后续建议"], [
        ["并发审批/签订", "后端使用加锁查询和事务控制状态前置条件", "增加并发 MockMvc 或数据库锁专项测试"],
        ["浏览器兼容性", "Playwright 使用 Chromium 验证主流程", "补充 Firefox/WebKit 冒烟，检查上传和下载行为"],
        ["大文件上传性能", "当前限制 10MB 并按 1MB 分片", "增加更大文件和弱网环境压测，观察临时分片清理"],
        ["权限配置误操作", "管理员保护和权限矩阵测试", "增加权限变更后的在线用户权限刷新测试"],
        ["导出文件编码", "CSV 使用 UTF-8 和转义", "用 Excel/WPS 实际打开样例文件检查中文和公式前缀"],
    ])


def manual(doc: Document) -> None:
    screenshot_dir = OUT / "manual-screenshots-jpg"

    def add_manual_shot(filename: str, caption: str) -> None:
        path = screenshot_dir / filename.replace(".png", ".jpg")
        if not path.exists():
            path = (OUT / "manual-screenshots") / filename
        if path.exists():
            add_picture(doc, path, 6.3)
            p = doc.add_paragraph(caption)
            p.alignment = WD_ALIGN_PARAGRAPH.CENTER
            p.runs[0].font.size = Pt(8.5)
            p.runs[0].font.color.rgb = MUTED

    doc.add_heading("1. 启动与登录", level=1)
    add_numbered(doc, [
        "在项目根目录执行 .\\scripts\\start-demo.ps1。若端口被占用，可使用 -BackendPort 和 -FrontendPort 参数指定备用端口。",
        "浏览器访问 http://localhost:5173。默认管理员账号为 admin / 123456；演示环境也可使用 start-demo.ps1 输出的 demo_* 账号。",
        "登录后系统会根据 JWT 中的用户身份加载当前用户，再由后端权限接口返回角色与权限点。没有权限的菜单不会显示，直接访问无权限路由会被重定向到工作台。",
        "进入工作台后先查看合同总数、待办任务、状态分布和月度合同量，确认后端、前端、统计接口和权限数据均已正常加载。",
    ])
    add_manual_shot("01-dashboard-statistics.png", "图 1 工作台：统计卡片、状态分布、月度合同量和最近合同")

    doc.add_heading("2. 模板管理演示", level=1)
    doc.add_paragraph("模板库用于沉淀标准合同文件。合同管理员可以上传、启停、删除模板，并设置可见角色；普通起草人进入模板库时只能看到自己角色可见且已启用的模板。")
    add_numbered(doc, [
        "使用管理员或合同管理员账号进入“模板库”。",
        "填写模板名称、说明、摘要，勾选可见角色；如不勾选角色，则模板对全部角色可见。",
        "选择 doc/docx/jpg/png/pdf 等受支持文件。系统会校验扩展名和文件内容签名，避免伪造扩展名的文件进入模板库。",
        "上传后在列表中查看模板名称、说明、原始文件名、文件大小、可见角色和启用状态；可点击下载验证文件保存正确。",
        "停用模板后，起草人侧不再展示该模板；重新启用后恢复可见。",
    ])
    add_manual_shot("02-template-management.png", "图 2 模板库：上传模板、角色可见性、启停和下载入口")

    doc.add_heading("3. 客户与合同起草", level=1)
    doc.add_paragraph("合同必须关联客户。实际演示时可先在“客户管理”新增客户，再进入“合同管理 -> 起草合同”。起草页面负责创建合同主数据，附件既可随起草提交，也可在草稿详情页继续维护。")
    add_numbered(doc, [
        "填写合同名称、客户、开始日期、结束日期和合同概述。结束日期早于开始日期时，后端会拒绝提交。",
        "需要参考标准文件时点击“模板库”下载模板，线下拟稿后再作为附件上传。",
        "点击“选择附件”可一次选择多个附件，普通起草入口适合小文件随合同一起提交。",
        "提交成功后系统生成合同编号、创建合同版本 V1、记录状态历史，并自动生成分配待办。",
    ])
    add_manual_shot("03-contract-create.png", "图 3 起草合同：基础字段、模板入口和附件选择区域")

    doc.add_heading("4. 附件分片续传演示", level=1)
    doc.add_paragraph("附件页是结题阶段新增能力的重点。系统不是只做普通上传，而是支持分片上传会话：前端按 1MB 切片，后端记录 uploadId、总分片数和已上传分片；中断后前端可查询已上传分片并继续上传，最后 complete 合并为正式附件。")
    add_numbered(doc, [
        "进入合同详情页的“附件”页签。只有合同起草人、具备 contract:update 权限且合同处于草稿状态时，才显示“选择文件”。",
        "选择文件后前端创建分片上传会话，并把 uploadId 临时保存到 localStorage，用于刷新页面后复用同一个会话。",
        "上传过程中进度会按已完成分片百分比显示；如果网络中断，再次选择同一文件时系统先查询已上传分片，只补传缺失分片。",
        "合并完成后附件列表展示文件名、大小、上传人、上传时间，并提供预览、下载、删除操作。",
        "后端会校验文件大小、扩展名和文件魔数；内容与扩展名不匹配的文件会被拒绝。",
    ])
    add_table(doc, ["接口", "用途", "演示观察点"], [
        ["POST /contracts/{id}/attachments/chunk-session", "创建上传会话", "返回 uploadId、chunkSize、totalChunks"],
        ["GET /attachments/chunk-session/{uploadId}", "查询会话状态", "返回 uploadedChunks，支持断点续传"],
        ["PUT /attachments/chunk-session/{uploadId}/chunks/{index}", "上传单个分片", "缺失分片可单独补传"],
        ["POST /attachments/chunk-session/{uploadId}/complete", "合并分片", "生成正式附件记录"],
    ])
    add_manual_shot("04-chunk-attachment-list.png", "图 4 附件页：分片上传完成后的附件列表")

    doc.add_heading("5. 合同分配与待办", level=1)
    doc.add_paragraph("合同分配由具备 contract:assign 权限的人员执行。系统会按权限筛选可分配候选人，避免把会签、审批、签订任务派给没有对应权限的用户。")
    add_numbered(doc, [
        "在合同详情页点击“分配人员”页签。",
        "会签人员支持多选，所有会签任务完成后合同才进入定稿环节。",
        "审批人员支持多选，所有审批人通过后才进入签订环节；任一审批人拒绝则合同进入拒绝状态。",
        "签订人员为单选，且不能是起草人，避免同一人完成起草和最终签订。",
        "点击确认分配后，分配任务变为已完成，系统生成会签待办，并把合同状态推进到已分配。",
    ])
    add_manual_shot("05-assign-users.png", "图 5 分配人员：会签、审批、签订候选人由权限过滤")

    doc.add_heading("6. 主流程操作", level=1)
    add_table(doc, ["环节", "操作人", "入口", "完成后状态", "可回退动作"], [
        ["分配", "合同管理员", "合同详情 -> 分配人员", "ASSIGNED / 已分配", "起草人可撤回合同"],
        ["会签", "会签人", "我的待办 -> 当前处理", "COUNTERSIGNED / 已会签", "会签人可打回到重新起草"],
        ["定稿", "起草人", "我的待办或合同详情 -> 当前处理", "FINALIZED / 已定稿", "起草人可在后续未完成前撤回任务"],
        ["审批", "审批人", "我的待办 -> 当前处理", "APPROVED / 已审批 或 RETURNED / 已打回", "审批人可打回到重新定稿或重新起草"],
        ["签订", "签订人", "我的待办 -> 当前处理", "SIGNED / 已签订", "签订前可打回，签订后流程结束"],
    ])
    add_manual_shot("06-rollback-round1-assigned.png", "图 6 回退模型初始状态：第 1 轮已分配，后续任务等待处理")
    add_manual_shot("07-approval-action-return.png", "图 7 审批处理页：审批人可选择通过、拒绝或打回")

    doc.add_heading("7. 回退模型完整演示", level=1)
    doc.add_paragraph("回退模型是本系统结题答辩的核心演示点。它解决的问题是：合同不是简单单向流转，实际业务中审批或签订环节经常要求补充材料、重新定稿或重新起草。系统通过 currentRound、returnTargetStage、ContractTask.round 和 SUPERSEDED 状态保留每一轮任务历史，并为恢复后的新轮次生成新的当前任务。")
    doc.add_heading("7.1 审批打回到重新定稿", level=2)
    add_numbered(doc, [
        "先完成分配、会签和第一轮定稿，使合同进入“已定稿”。",
        "审批人打开“当前处理”，在打回目标中选择“重新定稿”，填写打回原因。",
        "提交后合同状态变为“已打回”，回退目标显示“重新定稿”，起草人收到处理打回合同的待办。",
        "回退模型页仍显示第 1 轮任务：分配、会签、定稿、审批均保留处理人、状态、意见和时间。",
    ])
    add_manual_shot("08-returned-to-finalize.png", "图 8 已打回：回退目标为重新定稿，审批意见作为打回原因保留")
    doc.add_heading("7.2 起草人恢复流程并进入第 2 轮", level=2)
    add_numbered(doc, [
        "起草人查看打回原因，进入编辑合同或当前处理页补充合同内容。",
        "点击“恢复流程”后，系统读取 returnTargetStage；若目标为 FINALIZE，则进入第 2 轮定稿流程。",
        "第 1 轮任务不被覆盖，新的第 2 轮任务成为当前轮。页面会用“当前轮/历史轮”区分。",
        "恢复后保留原会签、审批、签订人员，减少重新分配成本，同时避免旧轮待办误操作。",
    ])
    add_manual_shot("09-resumed-round2.png", "图 9 恢复流程：currentRound 递增到第 2 轮，并展示当前轮任务")
    doc.add_heading("7.3 第 2 轮通过并签订", level=2)
    add_numbered(doc, [
        "起草人提交第二轮定稿，系统记录新版本。",
        "审批人审批通过后，签订人提交签订日期和签订说明。",
        "签订完成后合同状态变为“已签订”，主流程结束，页面仍可查看第 2 轮当前轮和第 1 轮历史轮。",
        "验收时重点确认：历史轮没有丢失，当前轮状态完整，保留分配人员清晰展示，回退目标在完成后清空。",
    ])
    add_manual_shot("10-signed-round2-complete.png", "图 10 签订完成：第 2 轮完成，同时保留第 1 轮历史")

    doc.add_heading("8. 时间线与版本历史", level=1)
    doc.add_paragraph("时间线和版本历史用于回答“合同为什么变成现在这个状态”和“每次修改了什么”。")
    add_numbered(doc, [
        "进入“流程时间线”页签，按时间顺序查看状态从起草、分配、会签、定稿、打回、恢复、审批到签订的变化。",
        "每条时间线记录包含起止状态、操作人、时间和备注，可用于答辩展示流程闭环。",
        "进入“版本历史”页签，查看起草、修改、定稿等动作产生的版本记录。",
        "版本历史不会替代附件，只记录合同核心字段和正文内容变化；附件变化在附件列表和操作日志中追踪。",
    ])
    add_manual_shot("11-contract-timeline.png", "图 11 流程时间线：状态变化和备注可追溯")
    add_manual_shot("12-contract-versions.png", "图 12 版本历史：起草、定稿和修改形成版本链")

    doc.add_heading("9. 查询、统计与导出", level=1)
    doc.add_paragraph("查询统计面向管理员、档案人员和答辩演示。它把原作业文档中“按合同编号和名称查询”扩展为多条件检索、状态统计、月度统计和 CSV 导出。")
    add_numbered(doc, [
        "进入“合同查询”，可按合同编号/名称、状态、客户、开始日期范围、结束日期范围组合筛选。",
        "点击“导出”下载 CSV。导出服务会对逗号、换行、引号和 Excel 公式前缀做转义，降低审计导出风险。",
        "工作台的状态分布和月度合同量来自统计接口，合同发生变更后通过事件监听使缓存失效。",
        "状态卡片可作为快速入口：点击某个状态后跳转到对应状态的合同列表。",
    ])
    add_manual_shot("13-contract-query-export.png", "图 13 合同查询：多条件检索和 CSV 导出")

    doc.add_heading("10. 操作日志与审计", level=1)
    doc.add_paragraph("操作日志记录用户、模块、动作、对象和内容，是结题答辩中证明“可追踪性”的功能。合同状态变化会以状态标签形式展示，便于定位关键操作。")
    add_numbered(doc, [
        "管理员进入“系统管理 -> 操作日志”。",
        "可按关键字、模块和时间范围筛选，例如只看 CONTRACT 模块或某个操作人。",
        "点击“导出 CSV”得到日志备份文件，用于归档或验收。",
        "日志由领域事件异步解耦记录，业务服务只发布操作事件，日志监听器负责落库。",
    ])
    add_manual_shot("14-operation-logs.png", "图 14 操作日志：按模块、关键字、时间过滤并导出")

    doc.add_heading("11. 常见问题", level=1)
    add_table(doc, ["问题", "原因", "处理"], [
        ["登录后没有菜单", "账号没有对应角色或权限点", "联系管理员在用户管理/角色管理中分配权限"],
        ["直接访问页面被跳回工作台", "前端路由权限不满足", "确认当前用户是否拥有页面 meta.permission 要求的权限"],
        ["接口返回 401", "JWT 缺失、过期或未登录", "重新登录，检查浏览器 sessionStorage 中 token 是否存在"],
        ["接口返回 403", "后端 @RequirePermission 拦截", "使用具备相应业务权限的账号操作"],
        ["附件上传失败", "文件过大、扩展名不支持或文件内容签名不匹配", "确认文件小于 10MB 且内容与扩展名一致"],
        ["待办消失", "任务已处理、被撤回或被新轮次封存", "到合同详情的回退模型/流程任务页查看历史轮和当前轮"],
        ["打回后不能恢复", "当前用户不是起草人，或缺少 contract:update 权限", "切换起草人账号，确认合同处于已打回或可恢复草稿状态"],
        ["签订后不能再编辑", "已签订合同为流程终态", "只能查看详情、时间线、版本和附件，不能继续修改流程"],
    ])


DIAGRAMS = make_diagrams()

save_doc("01-启动报告.docx", "启动报告", startup)
save_doc("02-关闭报告.docx", "关闭报告", closing)
save_doc("03-需求分析报告.docx", "需求分析报告", requirements)
save_doc("04-设计报告.docx", "设计报告", design)
save_doc("05-测试报告.docx", "测试报告", testing)
save_doc("06-用户使用手册.docx", "用户使用手册", manual)

print("Generated DOCX files in", OUT)
