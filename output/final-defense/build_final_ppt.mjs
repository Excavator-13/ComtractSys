import fs from "node:fs/promises";
import path from "node:path";
import { fileURLToPath } from "node:url";
import {
  Presentation,
  PresentationFile,
} from "file:///C:/Users/anwea/.cache/codex-runtimes/codex-primary-runtime/dependencies/node/node_modules/@oai/artifact-tool/dist/artifact_tool.mjs";

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const root = path.resolve(__dirname, "..", "..");
const outDir = path.resolve(root, "output", "final-defense");
const previewDir = path.join(outDir, "ppt-preview");
const layoutDir = path.join(outDir, "ppt-layout");
const midAssets = path.join(root, "outputs", "midterm-ppt", "assets");

await fs.mkdir(previewDir, { recursive: true });
await fs.mkdir(layoutDir, { recursive: true });

const C = {
  ink: "#0f172a",
  navy: "#111827",
  sidebar: "#152136",
  panel: "#f8fafc",
  line: "#d8e1ec",
  muted: "#64748b",
  teal: "#147d71",
  teal2: "#0f766e",
  amber: "#f59e0b",
  blue: "#2563eb",
  green: "#16a34a",
  red: "#dc2626",
  white: "#ffffff",
};

function shape(slide, cfg) {
  return slide.shapes.add({
    geometry: cfg.geometry || "rect",
    name: cfg.name,
    position: { left: cfg.x, top: cfg.y, width: cfg.w, height: cfg.h },
    fill: cfg.fill ?? "none",
    line: cfg.line ?? { style: "solid", fill: "none", width: 0 },
    borderRadius: cfg.borderRadius,
    shadow: cfg.shadow,
  });
}

function text(slide, cfg) {
  const box = shape(slide, {
    geometry: "textbox",
    x: cfg.x,
    y: cfg.y,
    w: cfg.w,
    h: cfg.h,
    fill: "none",
    line: { style: "solid", fill: "none", width: 0 },
    name: cfg.name,
  });
  box.text = cfg.text;
  box.text.style = {
    fontSize: cfg.fontSize || 20,
    bold: !!cfg.bold,
    color: cfg.color || C.ink,
    alignment: cfg.align || "left",
    typeface: "Microsoft YaHei",
  };
  return box;
}

function bg(slide) {
  shape(slide, { x: 0, y: 0, w: 1280, h: 720, fill: "#f3f6fa" });
  shape(slide, { x: 0, y: 0, w: 94, h: 720, fill: C.sidebar });
  shape(slide, { x: 94, y: 0, w: 1186, h: 18, fill: C.teal });
}

function footer(slide) {
  text(slide, {
    text: "ContractSys 合同管理系统 | 结题答辩",
    x: 130,
    y: 676,
    w: 520,
    h: 18,
    fontSize: 11,
    color: "#7b8794",
  });
}

let titleCounter = 2;
function title(slide, kicker, claim, page) {
  const actualPage = titleCounter++;
  text(slide, { text: kicker, x: 130, y: 48, w: 360, h: 22, fontSize: 14, color: C.teal, bold: true });
  text(slide, { text: claim, x: 130, y: 74, w: 930, h: 70, fontSize: 36, color: C.ink, bold: true });
  text(slide, { text: String(actualPage).padStart(2, "0"), x: 1162, y: 54, w: 58, h: 28, fontSize: 14, color: C.muted, align: "right" });
}

function card(slide, x, y, w, h, label, body, accent = C.teal) {
  shape(slide, { x, y, w, h, fill: C.white, line: { style: "solid", fill: C.line, width: 1 } });
  shape(slide, { x, y, w: 7, h, fill: accent });
  text(slide, { text: label, x: x + 22, y: y + 18, w: w - 44, h: 28, fontSize: 22, color: C.ink, bold: true });
  text(slide, { text: body, x: x + 22, y: y + 56, w: w - 44, h: h - 70, fontSize: 18, color: C.ink });
}

function metric(slide, x, y, value, label, color = C.teal) {
  text(slide, { text: value, x, y, w: 170, h: 42, fontSize: 34, color, bold: true });
  text(slide, { text: label, x, y: y + 48, w: 190, h: 22, fontSize: 15, color: C.muted });
}

function bullet(slide, x, y, value, color = C.teal) {
  shape(slide, { geometry: "ellipse", x, y: y + 8, w: 10, h: 10, fill: color });
  text(slide, { text: value, x: x + 22, y, w: 900, h: 28, fontSize: 21, color: C.ink });
}

function arrow(slide, x1, y, x2, color = C.muted) {
  shape(slide, { x: x1, y: y - 2, w: x2 - x1 - 12, h: 4, fill: color });
  shape(slide, { geometry: "triangle", x: x2 - 18, y: y - 8, w: 18, h: 18, fill: color });
}

async function image(slide, filename, x, y, w, h) {
  const file = path.join(midAssets, filename);
  const blob = await fs.readFile(file);
  slide.images.add({
    blob,
    contentType: "image/png",
    position: { left: x, top: y, width: w, height: h },
    fit: "contain",
  });
}

function flowNode(slide, x, y, label, status, color) {
  shape(slide, { x, y, w: 145, h: 74, fill: C.white, line: { style: "solid", fill: C.line, width: 1 } });
  shape(slide, { x, y, w: 145, h: 8, fill: color });
  text(slide, { text: label, x: x + 12, y: y + 18, w: 120, h: 24, fontSize: 22, color: C.ink, bold: true, align: "center" });
  text(slide, { text: status, x: x + 8, y: y + 48, w: 128, h: 18, fontSize: 11, color: C.muted, align: "center" });
}

const presentation = Presentation.create({ slideSize: { width: 1280, height: 720 } });

async function addSlide(render) {
  const slide = presentation.slides.add();
  bg(slide);
  await render(slide);
  footer(slide);
  return slide;
}

await addSlide((slide) => {
  text(slide, { text: "ContractSys", x: 130, y: 72, w: 620, h: 62, fontSize: 54, color: C.ink, bold: true });
  text(slide, { text: "合同管理系统结题答辩", x: 132, y: 148, w: 620, h: 38, fontSize: 30, color: C.teal, bold: true });
  shape(slide, { x: 130, y: 232, w: 660, h: 2, fill: C.line });
  text(slide, {
    text: "从中期流程闭环推进到结题验收。\n新增回退模型、分片续传、模板管理和状态机验证。",
    x: 130,
    y: 262,
    w: 700,
    h: 110,
    fontSize: 28,
    color: C.ink,
  });
  metric(slide, 132, 430, "131", "中期后变更文件", C.teal);
  metric(slide, 340, 430, "82", "后端测试用例", C.blue);
  metric(slide, 548, 430, "24613", "状态机可达状态", C.green);
  card(slide, 840, 116, 310, 370, "结题成果", "六份 Word 报告\n结题 PPTX\nMP4 演示脚本\n自动化验证材料", C.teal);
});

await addSlide((slide) => {
  title(slide, "MIDTERM DELTA", "中期后重点从功能闭环转向稳定可验收", 2);
  card(slide, 132, 180, 300, 138, "流程增强", "回退模型\n撤回任务\n多轮历史", C.teal);
  card(slide, 462, 180, 300, 138, "文件能力", "分片上传\n会话续传\n附件权限", C.blue);
  card(slide, 792, 180, 300, 138, "模板能力", "上传下载\n启停管理\n角色可见", C.green);
  card(slide, 132, 360, 300, 138, "查询审计", "高级查询\n日志导出\n统计缓存", C.amber);
  card(slide, 462, 360, 300, 138, "演示工程", "一键启动\n演示数据\nPlaywright", C.red);
  card(slide, 792, 360, 300, 138, "验证深化", "82 个测试\nBFS 预演\nDocker smoke", C.teal2);
});

await addSlide((slide) => {
  title(slide, "REQUIREMENT MATCH", "对照原作业文档，系统实现已经超出基础要求", 3);
  card(slide, 132, 170, 300, 140, "原要求：B/S + MySQL", "本系统：Vue 3 + Spring Boot 3\nFlyway 多数据库迁移\nDocker 可重复部署", C.blue);
  card(slide, 462, 170, 300, 140, "原要求：角色登录", "本系统：JWT 鉴权\nRBAC 权限点\n接口方法级授权", C.teal);
  card(slide, 792, 170, 300, 140, "原要求：合同流程", "本系统：任务表 + 状态历史\n多轮回退\n撤回/打回/恢复", C.green);
  card(slide, 132, 360, 300, 140, "原要求：附件上传", "本系统：格式与魔数校验\n分片上传\n中断后续传", C.amber);
  card(slide, 462, 360, 300, 140, "原要求：日志导出", "本系统：事件驱动日志\nCSV 转义\n版本历史追踪", C.red);
  card(slide, 792, 360, 300, 140, "原要求：查询统计", "本系统：高级筛选\n状态/月度统计\n缓存失效机制", C.teal2);
});

await addSlide((slide) => {
  title(slide, "INNOVATION", "创新点不是堆功能，而是解决真实业务边界", 4);
  card(slide, 132, 165, 460, 120, "1. 多轮工作流模型", "用 currentRound + task.round 表示流程轮次；旧轮任务封存而不是覆盖，拒绝原因和会签意见可追溯。", C.teal);
  card(slide, 650, 165, 460, 120, "2. PENDING 语义收敛", "待办、红点、按钮可用性都以“当前轮当前环节”为准，避免中期出现的提前审批/签订待办。", C.blue);
  card(slide, 132, 320, 460, 120, "3. 分片续传协议", "创建上传会话、记录分片 index、查询 uploadedChunks、完整后合并入附件表，浏览器中断后可继续。", C.green);
  card(slide, 650, 320, 460, 120, "4. 事件驱动审计", "合同变更只发布事件；操作日志、统计缓存失效由监听器处理，降低业务服务耦合。", C.amber);
  card(slide, 132, 475, 460, 120, "5. 状态机穷举验证", "workflow_sim.py 镜像后端流程，BFS 检查死锁、全局卡死和重复任务，覆盖人工演示难发现的路径。", C.red);
  card(slide, 650, 475, 460, 120, "6. 权限候选过滤", "分配人员按会签/审批/签订权限分别查询，错误在选择阶段被消除，而不是提交后才失败。", C.teal2);
});

await addSlide((slide) => {
  title(slide, "ARCHITECTURE", "系统保持前后端分离，后端按领域拆分", 3);
  const y = 255;
  card(slide, 140, y, 190, 110, "Vue 3", "页面\n路由\n状态", C.blue);
  arrow(slide, 346, y + 55, 406);
  card(slide, 420, y, 210, 110, "Spring Boot", "接口\n服务\n事务", C.teal);
  arrow(slide, 648, y + 55, 708);
  card(slide, 722, y, 190, 110, "Security", "JWT\nRBAC\n授权", C.green);
  arrow(slide, 930, y + 55, 990);
  card(slide, 1004, y, 170, 110, "Database", "Flyway\nRedis\n历史表", C.amber);
  card(slide, 150, 438, 1000, 100, "领域拆分", "auth / contract / customer / user / log / common / config", C.teal);
});

await addSlide((slide) => {
  title(slide, "WORKFLOW", "回退模型让合同流程支持多轮处理", 4);
  const y = 250;
  flowNode(slide, 132, y, "起草", "DRAFT", C.blue);
  arrow(slide, 288, y + 36, 304);
  flowNode(slide, 318, y, "会签", "ASSIGNED", C.teal);
  arrow(slide, 474, y + 36, 490);
  flowNode(slide, 504, y, "定稿", "COUNTERSIGNED", C.amber);
  arrow(slide, 660, y + 36, 676);
  flowNode(slide, 690, y, "审批", "FINALIZED", C.green);
  arrow(slide, 846, y + 36, 862);
  flowNode(slide, 876, y, "签订", "SIGNED", C.teal);
  card(slide, 250, 420, 260, 110, "打回", "审批/签订可打回到起草或定稿", C.red);
  card(slide, 540, 420, 260, 110, "恢复", "起草人修改后开启新轮次", C.blue);
  card(slide, 830, 420, 260, 110, "撤回", "下一环节未完成时可撤回任务", C.green);
});

await addSlide((slide) => {
  title(slide, "UPLOAD", "附件分片上传支持中断后续传", 5);
  card(slide, 132, 188, 290, 150, "创建会话", "记录上传会话 ID\n文件名/大小\n分片数量", C.teal);
  arrow(slide, 440, 263, 488);
  card(slide, 506, 188, 290, 150, "上传分片", "1MB 切片\n按 index 上传\n查询已传分片", C.blue);
  arrow(slide, 814, 263, 862);
  card(slide, 880, 188, 290, 150, "合并入库", "校验大小\n复用文件校验\n生成附件记录", C.green);
  bullet(slide, 180, 430, "前端使用 localStorage 保存 uploadId，同一文件重新选择时可复用上传会话。", C.teal);
  bullet(slide, 180, 470, "后端限制分片参数、上传人和合同状态，只有起草阶段起草人可修改附件。", C.blue);
  bullet(slide, 180, 510, "结题材料按“分片上传 + 会话续传”表述，不夸大为完整秒传方案。", C.amber);
});

await addSlide(async (slide) => {
  title(slide, "SCREENS", "界面从列表管理扩展到流程可视化", 6);
  await image(slide, "11-dashboard-admin.png", 132, 165, 310, 220);
  await image(slide, "13-contract-timeline.png", 472, 165, 310, 220);
  await image(slide, "14-contract-versions.png", 812, 165, 310, 220);
  await image(slide, "15-contract-attachments.png", 302, 430, 310, 160);
  await image(slide, "17-operation-logs.png", 650, 430, 310, 160);
});

await addSlide((slide) => {
  title(slide, "SECURITY", "权限控制同时覆盖菜单、接口和业务数据", 7);
  card(slide, 140, 210, 220, 128, "JWT", "签名校验\n过期控制\n401 跳转", C.blue);
  card(slide, 390, 210, 220, 128, "RBAC", "用户-角色\n角色-权限\n方法授权", C.teal);
  card(slide, 640, 210, 220, 128, "数据权限", "任务归属\n起草人限制\n合同可见性", C.green);
  card(slide, 890, 210, 220, 128, "审计", "事件日志\nCSV 导出\n操作追踪", C.amber);
  text(slide, { text: "安全边界不只依赖前端菜单隐藏，后端每个关键接口都通过权限点和业务状态二次校验。", x: 190, y: 470, w: 850, h: 44, fontSize: 26, color: C.ink, align: "center" });
});

await addSlide((slide) => {
  title(slide, "DATA MODEL", "数据库围绕权限、合同、流程和审计扩展", 8);
  card(slide, 130, 210, 220, 150, "权限表", "sys_user\nsys_role\nsys_permission\n多对多关联", C.blue);
  card(slide, 386, 210, 220, 150, "业务主表", "customer\ncontract\nattachment\n模板文件", C.teal);
  card(slide, 642, 210, 220, 150, "流程表", "contract_task\ncurrent_round\nstate_history", C.green);
  card(slide, 898, 210, 220, 150, "审计扩展", "operation_log\ncontract_version\nstatistics cache", C.amber);
  text(slide, { text: "Flyway 同步维护 H2、MySQL、PostgreSQL 迁移脚本，便于本地测试、联调和容器部署。", x: 190, y: 500, w: 850, h: 42, fontSize: 25, color: C.ink, align: "center" });
});

await addSlide((slide) => {
  title(slide, "VERIFICATION", "测试从接口边界扩展到状态机穷举", 9);
  metric(slide, 154, 184, "82", "后端测试用例", C.blue);
  metric(slide, 354, 184, "0", "非截断死锁", C.green);
  metric(slide, 554, 184, "24613", "可达状态", C.teal);
  metric(slide, 790, 184, "MP4", "演示视频产物", C.red);
  card(slide, 150, 340, 295, 138, "集成测试", "权限矩阵\n边界校验\n流程回归", C.blue);
  card(slide, 482, 340, 295, 138, "状态机预演", "BFS 穷举\n重复任务检测\n卡死路径验证", C.green);
  card(slide, 814, 340, 295, 138, "端到端演示", "Playwright\n延时录制\n截图与日志", C.teal);
});

await addSlide((slide) => {
  title(slide, "DEMO VIDEO", "演示视频以新增功能为主线", 10);
  shape(slide, { x: 150, y: 170, w: 960, h: 390, fill: "#0f172a", line: { style: "solid", fill: C.line, width: 1 } });
  text(slide, { text: "contractsys-final-demo.mp4", x: 250, y: 300, w: 760, h: 50, fontSize: 42, color: C.white, bold: true, align: "center" });
  text(slide, { text: "录制脚本生成同目录 MP4；若需嵌入媒体，请在 PowerPoint 中插入该文件。", x: 255, y: 368, w: 750, h: 34, fontSize: 22, color: "#cbd5e1", align: "center" });
});

await addSlide((slide) => {
  title(slide, "TEAM", "分工沿用中期角色，姓名待补充", 11);
  card(slide, 132, 170, 200, 150, "项目负责人", "进度统筹\n文档整合\n答辩组织", C.blue);
  card(slide, 368, 170, 200, 150, "后端开发", "认证权限\n合同流程\n附件上传", C.teal);
  card(slide, 604, 170, 200, 150, "前端开发", "页面交互\n流程可视化\nPPT素材", C.green);
  card(slide, 840, 170, 200, 150, "测试", "测试用例\nPlaywright\n回归验证", C.amber);
  card(slide, 486, 390, 300, 150, "数据库/运维", "Flyway\nDocker\n启动脚本", C.red);
  text(slide, { text: "真实姓名未在中期 PPT 或仓库中出现，当前先保留角色占位。", x: 190, y: 586, w: 850, h: 38, fontSize: 24, color: C.ink, align: "center" });
});

await addSlide((slide) => {
  title(slide, "SUMMARY", "结题版本完成可演示、可追踪、可验证的合同系统", 12);
  card(slide, 154, 202, 280, 190, "业务完整性", "客户、合同、模板、附件、流程、日志均形成闭环", C.blue);
  card(slide, 500, 202, 280, 190, "工程完整性", "权限、事务、缓存、迁移、脚本和测试共同支撑", C.teal);
  card(slide, 846, 202, 280, 190, "答辩完整性", "Word 报告、PPTX、MP4 脚本和验证证据齐备", C.green);
  text(slide, { text: "后续可扩展邮件通知、电子签章、对象存储和 AI 合同审查。", x: 190, y: 522, w: 850, h: 38, fontSize: 25, color: C.ink, align: "center" });
});

for (const [index, slide] of presentation.slides.items.entries()) {
  const stem = `slide-${String(index + 1).padStart(2, "0")}`;
  const png = await presentation.export({ slide, format: "png", scale: 1 });
  await fs.writeFile(path.join(previewDir, `${stem}.png`), new Uint8Array(await png.arrayBuffer()));
  const layout = await slide.export({ format: "layout" });
  await fs.writeFile(path.join(layoutDir, `${stem}.layout.json`), await layout.text());
}

const montage = await presentation.export({ format: "webp", montage: true, scale: 1 });
await fs.writeFile(path.join(previewDir, "contact-sheet.webp"), new Uint8Array(await montage.arrayBuffer()));

const pptx = await PresentationFile.exportPptx(presentation);
await pptx.save(path.join(outDir, "合同管理系统结题答辩.pptx"));
console.log("Generated PPTX in", outDir);
