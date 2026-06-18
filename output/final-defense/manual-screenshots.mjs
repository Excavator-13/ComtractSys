import { chromium } from "playwright";
import fs from "node:fs/promises";
import path from "node:path";

const WEB = process.env.WEB_URL || "http://127.0.0.1:5173";
const API = process.env.API_URL || "http://127.0.0.1:8080/api/v1";
const OUT = path.resolve("output/final-defense/manual-screenshots");
const suffix = Date.now().toString(36);

async function api(pathname, { method = "GET", token, body, headers = {} } = {}) {
  const jsonBody = body && !(body instanceof FormData) ? Buffer.from(JSON.stringify(body), "utf8") : undefined;
  const res = await fetch(`${API}${pathname}`, {
    method,
    headers: {
      ...(jsonBody ? { "Content-Type": "application/json" } : {}),
      ...(token ? { Authorization: `Bearer ${token}` } : {}),
      ...headers,
    },
    body: body instanceof FormData ? body : jsonBody,
  });
  const data = await res.json().catch(() => null);
  if (!res.ok || data?.code !== 0) {
    throw new Error(`${method} ${pathname} -> ${res.status}: ${data?.message || res.statusText}`);
  }
  return data.data;
}

async function login(username, password = "123456") {
  return api("/auth/login", { method: "POST", body: { username, password } });
}

async function createUser(adminToken, username, roleId) {
  return api("/users", {
    method: "POST",
    token: adminToken,
    body: { username, password: "123456", displayName: username, roleIds: [roleId] },
  });
}

async function createTemplate(adminToken) {
  const form = new FormData();
  form.append("name", `手册演示模板-${suffix}`);
  form.append("description", "用于用户使用手册截图，演示模板上传、角色可见和启停下载。");
  form.append("content", "适用于采购、服务和验收类合同。");
  form.append("visibleRoles", "ROLE_OPERATOR");
  form.append("file", new Blob(["%PDF-1.4\n% manual template\n1 0 obj\n<<>>\nendobj\n%%EOF"], { type: "application/pdf" }), `manual-template-${suffix}.pdf`);
  return api("/contract-templates", { method: "POST", token: adminToken, body: form });
}

async function uploadChunkedAttachment(token, contractId) {
  const content = "%PDF-1.4\n% ContractSys manual chunk upload demo\n" + "0".repeat(1400 * 1024);
  const file = new Blob([content], { type: "application/pdf" });
  const chunkSize = 1024 * 1024;
  const totalChunks = Math.ceil(file.size / chunkSize);
  const session = await api(`/contracts/${contractId}/attachments/chunk-session`, {
    method: "POST",
    token,
    body: {
      originalName: `手册分片续传演示-${suffix}.pdf`,
      fileSize: file.size,
      contentType: "application/pdf",
      chunkSize,
      totalChunks,
    },
  });
  for (let index = 0; index < totalChunks; index += 1) {
    const form = new FormData();
    form.append("chunk", file.slice(index * chunkSize, Math.min(file.size, (index + 1) * chunkSize)), `part-${index}`);
    await api(`/attachments/chunk-session/${session.uploadId}/chunks/${index}`, {
      method: "PUT",
      token,
      body: form,
    });
  }
  await api(`/attachments/chunk-session/${session.uploadId}/complete`, { method: "POST", token });
}

async function seed() {
  const adminLogin = await login("admin");
  const adminToken = adminLogin.token;
  const roles = await api("/roles", { token: adminToken });
  const operatorRole = roles.find((role) => role.roleCode === "ROLE_OPERATOR");
  const managerRole = roles.find((role) => role.roleCode === "ROLE_CONTRACT_ADMIN");
  if (!operatorRole || !managerRole) throw new Error("缺少演示角色 ROLE_OPERATOR / ROLE_CONTRACT_ADMIN");

  const drafter = await createUser(adminToken, `manual_drafter_${suffix}`, operatorRole.id);
  const counter = await createUser(adminToken, `manual_counter_${suffix}`, operatorRole.id);
  const approver = await createUser(adminToken, `manual_approver_${suffix}`, operatorRole.id);
  const signer = await createUser(adminToken, `manual_signer_${suffix}`, operatorRole.id);
  const manager = await createUser(adminToken, `manual_manager_${suffix}`, managerRole.id);
  const customer = await api("/customers", {
    method: "POST",
    token: adminToken,
    body: {
      name: `手册演示客户-${suffix}`,
      tel: "13800000000",
      address: "手册演示地址",
      bankName: "演示银行",
      bankAccount: "622200000000",
      fax: "010-00000000",
      postalCode: "100000",
      remark: "用户手册截图数据",
    },
  });
  await createTemplate(adminToken);

  const drafterLogin = await login(drafter.username);
  const contract = await api("/contracts", {
    method: "POST",
    token: drafterLogin.token,
    body: {
      name: `回退模型完整演示合同-${suffix}`,
      customerId: customer.id,
      beginDate: "2026-06-18",
      endDate: "2027-06-18",
      content: "第一轮起草：用于展示分配、会签、审批打回、恢复新轮、版本历史和分片附件。",
    },
  });
  await uploadChunkedAttachment(drafterLogin.token, contract.id);
  return { adminLogin, adminToken, drafter, counter, approver, signer, manager, contract };
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

async function shot(page, name) {
  await page.waitForLoadState("networkidle").catch(() => {});
  await page.waitForTimeout(900);
  await page.screenshot({ path: path.join(OUT, `${name}.png`), fullPage: true });
}

async function go(page, loginResponse, url, name) {
  await setSession(page, loginResponse);
  await page.goto(url);
  await shot(page, name);
}

async function main() {
  await fs.mkdir(OUT, { recursive: true });
  const data = await seed();
  const browser = await chromium.launch({ headless: true });
  const page = await browser.newPage({ viewport: { width: 1440, height: 1100 }, deviceScaleFactor: 1 });

  await go(page, data.adminLogin, `${WEB}/dashboard`, "01-dashboard-statistics");
  await go(page, data.adminLogin, `${WEB}/templates`, "02-template-management");
  await go(page, await login(data.drafter.username), `${WEB}/contracts/create`, "03-contract-create");
  await go(page, await login(data.drafter.username), `${WEB}/contracts/${data.contract.id}?tab=attachments`, "04-chunk-attachment-list");
  await go(page, await login(data.manager.username), `${WEB}/contracts/${data.contract.id}?tab=assign`, "05-assign-users");

  await api(`/contracts/${data.contract.id}/assign`, {
    method: "POST",
    token: (await login(data.manager.username)).token,
    body: {
      countersignUserIds: [data.counter.id],
      approvalUserIds: [data.approver.id],
      signUserId: data.signer.id,
    },
  });
  await go(page, await login(data.manager.username), `${WEB}/contracts/${data.contract.id}?tab=rollback`, "06-rollback-round1-assigned");

  await api(`/contracts/${data.contract.id}/countersign`, {
    method: "POST",
    token: (await login(data.counter.username)).token,
    body: { opinion: "会签通过：条款完整，可进入起草人定稿。" },
  });
  await api(`/contracts/${data.contract.id}/finalize`, {
    method: "POST",
    token: (await login(data.drafter.username)).token,
    body: { content: "第一轮定稿：已吸收会签意见，提交审批。" },
  });
  await go(page, await login(data.approver.username), `${WEB}/contracts/${data.contract.id}?tab=approve`, "07-approval-action-return");

  await api(`/contracts/${data.contract.id}/return`, {
    method: "POST",
    token: (await login(data.approver.username)).token,
    body: { targetStage: "FINALIZE", opinion: "审批打回：请补充交付验收与付款约束。" },
  });
  await go(page, await login(data.drafter.username), `${WEB}/contracts/${data.contract.id}?tab=rollback`, "08-returned-to-finalize");

  await api(`/contracts/${data.contract.id}/resume`, {
    method: "POST",
    token: (await login(data.drafter.username)).token,
  });
  await go(page, await login(data.drafter.username), `${WEB}/contracts/${data.contract.id}?tab=rollback`, "09-resumed-round2");

  await api(`/contracts/${data.contract.id}/finalize`, {
    method: "POST",
    token: (await login(data.drafter.username)).token,
    body: { content: "第二轮定稿：补充验收标准、付款节点和违约责任后再次提交审批。" },
  });
  await api(`/contracts/${data.contract.id}/approve`, {
    method: "POST",
    token: (await login(data.approver.username)).token,
    body: { result: "APPROVED", opinion: "审批通过：修改内容满足要求。" },
  });
  await api(`/contracts/${data.contract.id}/sign`, {
    method: "POST",
    token: (await login(data.signer.username)).token,
    body: { signInfo: "双方线上确认，纸质合同已归档。", signedDate: "2026-06-18" },
  });
  await go(page, data.adminLogin, `${WEB}/contracts/${data.contract.id}?tab=rollback`, "10-signed-round2-complete");
  await go(page, data.adminLogin, `${WEB}/contracts/${data.contract.id}?tab=timeline`, "11-contract-timeline");
  await go(page, data.adminLogin, `${WEB}/contracts/${data.contract.id}?tab=versions`, "12-contract-versions");
  await go(page, data.adminLogin, `${WEB}/contracts/query`, "13-contract-query-export");
  await go(page, data.adminLogin, `${WEB}/system/logs`, "14-operation-logs");

  await browser.close();
  console.log(`Manual screenshots written to ${OUT}`);
}

main().catch((error) => {
  console.error(error);
  process.exitCode = 1;
});
