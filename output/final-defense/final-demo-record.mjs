import { chromium } from "playwright";
import fs from "node:fs/promises";
import path from "node:path";
import { spawn } from "node:child_process";

const WEB = process.env.WEB_URL || "http://127.0.0.1:5173";
const API = process.env.API_URL || "http://127.0.0.1:8080/api/v1";
const OUT = path.resolve("output/final-defense");
const VIDEO_DIR = path.join(OUT, "recording-raw");
const MP4 = path.join(OUT, "contractsys-final-demo.mp4");
const suffix = Date.now().toString(36);

async function api(pathname, { method = "GET", token, body } = {}) {
  const res = await fetch(`${API}${pathname}`, {
    method,
    headers: {
      "Content-Type": "application/json",
      ...(token ? { Authorization: `Bearer ${token}` } : {}),
    },
    body: body ? JSON.stringify(body) : undefined,
  });
  const data = await res.json().catch(() => null);
  if (!res.ok || data?.code !== 0) {
    throw new Error(`${method} ${pathname} -> ${res.status}: ${data?.message || res.statusText}`);
  }
  return data.data;
}

async function apiForm(pathname, { token, form } = {}) {
  const res = await fetch(`${API}${pathname}`, {
    method: "POST",
    headers: token ? { Authorization: `Bearer ${token}` } : {},
    body: form,
  });
  const data = await res.json().catch(() => null);
  if (!res.ok || data?.code !== 0) {
    throw new Error(`POST ${pathname} -> ${res.status}: ${data?.message || res.statusText}`);
  }
  return data.data;
}

async function login(username, password = "123456") {
  return api("/auth/login", { method: "POST", body: { username, password } });
}

async function slow(page, ms = 1200) {
  await page.waitForTimeout(ms);
}

async function setSession(page, loginResponse) {
  await page.goto(`${WEB}/login`);
  await page.evaluate(({ token, user }) => {
    sessionStorage.setItem("token", token);
    sessionStorage.setItem("user", JSON.stringify(user));
    localStorage.setItem("token", token);
    localStorage.setItem("user", JSON.stringify(user));
  }, loginResponse);
}

async function createUser(adminToken, username, roleId) {
  return api("/users", {
    method: "POST",
    token: adminToken,
    body: {
      username,
      password: "123456",
      displayName: username,
      roleIds: [roleId],
    },
  });
}

async function ensureDemoData() {
  const adminLogin = await login("admin");
  const adminToken = adminLogin.token;
  const roles = await api("/roles", { token: adminToken });
  const operatorRole = roles.find((r) => r.roleCode === "ROLE_OPERATOR");
  const managerRole = roles.find((r) => r.roleCode === "ROLE_CONTRACT_ADMIN");
  if (!operatorRole || !managerRole) throw new Error("缺少演示角色");

  const drafter = await createUser(adminToken, `final_drafter_${suffix}`, operatorRole.id);
  const countersigner = await createUser(adminToken, `final_counter_${suffix}`, operatorRole.id);
  const approver = await createUser(adminToken, `final_approve_${suffix}`, operatorRole.id);
  const signer = await createUser(adminToken, `final_sign_${suffix}`, operatorRole.id);
  const manager = await createUser(adminToken, `final_manager_${suffix}`, managerRole.id);
  const customer = await api("/customers", {
    method: "POST",
    token: adminToken,
    body: {
      name: `结题演示客户${suffix}`,
      tel: "13800000000",
      address: "结题答辩演示地址",
      bankName: "演示银行",
      bankAccount: "622200000000",
      fax: "010-00000000",
      postalCode: "100000",
      remark: "结题演示数据",
    },
  });

  const templateForm = new FormData();
  templateForm.append("name", `结题演示模板${suffix}`);
  templateForm.append("description", "结题答辩模板管理演示");
  templateForm.append("content", "结题答辩模板概述");
  templateForm.append("visibleRoles", "ROLE_OPERATOR");
  templateForm.append("file", new Blob(["%PDF-1.4\nfinal template"], { type: "application/pdf" }), `final-template-${suffix}.pdf`);
  const template = await apiForm("/contract-templates", { token: adminToken, form: templateForm });

  const drafterLogin = await login(drafter.username);
  const contract = await api("/contracts", {
    method: "POST",
    token: drafterLogin.token,
    body: {
      name: `结题演示合同${suffix}`,
      customerId: customer.id,
      beginDate: "2026-06-17",
      endDate: "2027-06-17",
      content: "用于结题答辩演示的合同概述，覆盖流程回退、附件续传、版本历史和日志审计。",
    },
  });

  return { adminLogin, adminToken, drafter, countersigner, approver, signer, manager, customer, template, contract };
}

async function convertToMp4(webmPath) {
  await fs.rm(MP4, { force: true });
  const encoder = await new Promise((resolve) => {
    const probe = spawn("ffmpeg", ["-hide_banner", "-encoders"], { stdio: ["ignore", "pipe", "ignore"] });
    let out = "";
    probe.stdout.on("data", (chunk) => (out += chunk.toString()));
    probe.on("exit", () => resolve(out.includes("libx264") ? "libx264" : "mpeg4"));
  });
  const args = encoder === "libx264"
    ? ["-y", "-i", webmPath, "-c:v", "libx264", "-pix_fmt", "yuv420p", "-movflags", "+faststart", MP4]
    : ["-y", "-i", webmPath, "-c:v", "mpeg4", "-q:v", "5", "-pix_fmt", "yuv420p", "-movflags", "+faststart", MP4];
  return new Promise((resolve, reject) => {
    const ffmpeg = spawn("ffmpeg", args, { stdio: "inherit" });
    ffmpeg.on("exit", (code) => (code === 0 ? resolve() : reject(new Error(`ffmpeg exited ${code}`))));
  });
}

async function main() {
  await fs.mkdir(VIDEO_DIR, { recursive: true });
  const data = await ensureDemoData();

  const browser = await chromium.launch({ headless: true });
  const context = await browser.newContext({
    viewport: { width: 1440, height: 900 },
    recordVideo: { dir: VIDEO_DIR, size: { width: 1440, height: 900 } },
  });
  const page = await context.newPage();

  await page.goto(`${WEB}/login`);
  await slow(page, 1000);
  await page.locator("input").nth(0).fill("admin");
  await page.locator("input").nth(1).fill("123456");
  await slow(page, 700);
  await page.getByRole("button", { name: "登录" }).click();
  await page.waitForURL(/dashboard/, { timeout: 10000 });
  await slow(page, 1800);

  await page.goto(`${WEB}/templates`);
  await slow(page, 2200);
  await page.goto(`${WEB}/contracts`);
  await slow(page, 1800);

  await setSession(page, await login(data.drafter.username));
  await page.goto(`${WEB}/contracts/create`);
  await slow(page, 1800);
  await page.goto(`${WEB}/contracts/${data.contract.id}?tab=attachments`);
  await slow(page, 1800);

  await setSession(page, await login(data.manager.username));
  await page.goto(`${WEB}/contracts/${data.contract.id}?tab=assign`);
  await slow(page, 1600);
  await api(`/contracts/${data.contract.id}/assign`, {
    method: "POST",
    token: (await login(data.manager.username)).token,
    body: {
      countersignUserIds: [data.countersigner.id],
      approvalUserIds: [data.approver.id],
      signUserId: data.signer.id,
    },
  });
  await page.reload();
  await slow(page, 1800);

  await api(`/contracts/${data.contract.id}/countersign`, {
    method: "POST",
    token: (await login(data.countersigner.username)).token,
    body: { opinion: "会签通过，建议进入定稿。" },
  });
  await setSession(page, await login(data.drafter.username));
  await page.goto(`${WEB}/contracts/${data.contract.id}?tab=rollback`);
  await slow(page, 1800);
  await api(`/contracts/${data.contract.id}/finalize`, {
    method: "POST",
    token: (await login(data.drafter.username)).token,
    body: { content: "定稿版本：已吸收会签意见，提交审批。" },
  });

  await api(`/contracts/${data.contract.id}/return`, {
    method: "POST",
    token: (await login(data.approver.username)).token,
    body: { targetStage: "FINALIZE", opinion: "审批打回：请补充交付验收说明。" },
  });
  await page.goto(`${WEB}/contracts/${data.contract.id}?tab=rollback`);
  await slow(page, 2400);

  await api(`/contracts/${data.contract.id}/resume`, {
    method: "POST",
    token: (await login(data.drafter.username)).token,
  });
  await api(`/contracts/${data.contract.id}/finalize`, {
    method: "POST",
    token: (await login(data.drafter.username)).token,
    body: { content: "第二轮定稿：已补充交付验收说明。" },
  });
  await api(`/contracts/${data.contract.id}/approve`, {
    method: "POST",
    token: (await login(data.approver.username)).token,
    body: { result: "APPROVED", opinion: "审批通过。" },
  });
  await api(`/contracts/${data.contract.id}/sign`, {
    method: "POST",
    token: (await login(data.signer.username)).token,
    body: { signInfo: "结题演示签订完成。", signedDate: "2026-06-17" },
  });

  await setSession(page, data.adminLogin);
  await page.goto(`${WEB}/contracts/${data.contract.id}?tab=rollback`);
  await slow(page, 2600);
  await page.goto(`${WEB}/contracts/query`);
  await slow(page, 1600);
  await page.goto(`${WEB}/system/logs`);
  await slow(page, 2200);

  const video = page.video();
  await context.close();
  await browser.close();
  const webmPath = await video.path();
  await convertToMp4(webmPath);
  console.log(`MP4 written to ${MP4}`);
}

main().catch((error) => {
  console.error(error);
  process.exitCode = 1;
});
