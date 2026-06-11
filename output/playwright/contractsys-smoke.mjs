import { chromium } from 'playwright';

const WEB = process.env.WEB_URL || 'http://127.0.0.1:5173';
const API = process.env.API_URL || 'http://127.0.0.1:8080/api/v1';
const suffix = Date.now().toString(36);

const results = [];
function ok(name, detail = '') {
  results.push({ name, ok: true, detail });
  console.log(`[PASS] ${name}${detail ? ` - ${detail}` : ''}`);
}
function fail(name, error) {
  const detail = error?.message || String(error);
  results.push({ name, ok: false, detail });
  console.error(`[FAIL] ${name} - ${detail}`);
}
async function step(name, fn) {
  try {
    const detail = await fn();
    ok(name, detail);
  } catch (error) {
    fail(name, error);
  }
}

async function api(path, { method = 'GET', token, body } = {}) {
  const res = await fetch(`${API}${path}`, {
    method,
    headers: {
      'Content-Type': 'application/json',
      ...(token ? { Authorization: `Bearer ${token}` } : {})
    },
    body: body ? JSON.stringify(body) : undefined
  });
  const data = await res.json().catch(() => null);
  if (!res.ok || data?.code !== 0) {
    throw new Error(`${method} ${path} -> ${res.status}: ${data?.message || res.statusText}`);
  }
  return data.data;
}

async function apiForm(path, { method = 'POST', token, form } = {}) {
  const res = await fetch(`${API}${path}`, {
    method,
    headers: token ? { Authorization: `Bearer ${token}` } : {},
    body: form
  });
  const data = await res.json().catch(() => null);
  if (!res.ok || data?.code !== 0) {
    throw new Error(`${method} ${path} -> ${res.status}: ${data?.message || res.statusText}`);
  }
  return data.data;
}

async function apiDownload(path, token) {
  const res = await fetch(`${API}${path}`, {
    headers: token ? { Authorization: `Bearer ${token}` } : {}
  });
  if (!res.ok) throw new Error(`GET ${path} -> ${res.status}: ${res.statusText}`);
  return {
    contentType: res.headers.get('content-type') || '',
    disposition: res.headers.get('content-disposition') || '',
    bytes: await res.arrayBuffer()
  };
}

async function loginApi(username, password = 'test-pass-123') {
  return api('/auth/login', { method: 'POST', body: { username, password } });
}

async function setSession(page, loginResponse) {
  await page.goto(`${WEB}/login`);
  await page.evaluate(({ token, user }) => {
    localStorage.setItem('token', token);
    localStorage.setItem('user', JSON.stringify(user));
  }, loginResponse);
}

async function createUser(adminToken, username, roleId) {
  return api('/users', {
    method: 'POST',
    token: adminToken,
    body: {
      username,
      password: 'test-pass-123',
      displayName: username,
      roleIds: [roleId]
    }
  });
}

async function main() {
  const adminLogin = await api('/auth/login', {
    method: 'POST',
    body: { username: 'admin', password: '123456' }
  });
  const adminToken = adminLogin.token;
  const roles = await api('/roles', { token: adminToken });
  const operatorRole = roles.find(r => r.roleCode === 'ROLE_OPERATOR');
  const contractAdminRole = roles.find(r => r.roleCode === 'ROLE_CONTRACT_ADMIN');
  if (!operatorRole || !contractAdminRole) throw new Error('缺少演示角色');

  const drafter = await createUser(adminToken, `pw_drafter_${suffix}`, operatorRole.id);
  const countersigner = await createUser(adminToken, `pw_cs_${suffix}`, operatorRole.id);
  const approver = await createUser(adminToken, `pw_ap_${suffix}`, operatorRole.id);
  const signer = await createUser(adminToken, `pw_sign_${suffix}`, operatorRole.id);
  const assigner = await createUser(adminToken, `pw_assign_${suffix}`, contractAdminRole.id);
  const customer = await api('/customers', {
    method: 'POST',
    token: adminToken,
    body: {
      name: `PW客户${suffix}`,
      tel: '13800000000',
      address: 'Playwright测试地址',
      bankName: 'Playwright银行',
      bankAccount: '622200000000',
      fax: '010-00000000',
      postalCode: '100000',
      remark: '客户详情展开测试'
    }
  });

  const browser = await chromium.launch({ headless: true });
  const context = await browser.newContext({ acceptDownloads: true });
  const page = await context.newPage();
  const consoleErrors = [];
  page.on('console', msg => {
    if (msg.type() === 'error') consoleErrors.push(msg.text());
  });
  page.on('pageerror', err => consoleErrors.push(err.message));

  await step('登录页真实表单可登录', async () => {
    await page.goto(`${WEB}/login`);
    const inputs = page.locator('input');
    await inputs.nth(0).fill('admin');
    await inputs.nth(1).fill('123456');
    await page.getByRole('button', { name: '登录' }).click();
    await page.waitForURL(/dashboard/, { timeout: 10000 });
    await page.waitForLoadState('networkidle');
    return page.url();
  });

  await step('合同管理状态入口可渲染', async () => {
    await page.goto(`${WEB}/contracts?status=RETURNED`);
    await page.waitForLoadState('networkidle');
    const text = await page.locator('body').innerText();
    if (!text.includes('合同管理') || !text.includes('已打回') || !text.includes('合同期限')) {
      throw new Error('合同管理页缺少状态入口或合同期限列');
    }
  });

  await step('权限页无新增/删除入口', async () => {
    await page.goto(`${WEB}/system/permissions`);
    await page.waitForLoadState('networkidle');
    const text = await page.locator('body').innerText();
    const buttonTexts = await page.locator('button').evaluateAll(buttons =>
      buttons.map(button => button.textContent?.trim()).filter(Boolean)
    );
    if (buttonTexts.some(label => label.includes('新增权限') || label === '删除')) {
      throw new Error('权限页仍暴露新增或删除入口');
    }
    if (!text.includes('系统权限')) throw new Error('权限页未显示 core 类型标记');
  });

  await step('个人设置面板可打开', async () => {
    await page.goto(`${WEB}/dashboard`);
    await page.waitForLoadState('networkidle');
    await page.getByTitle('个人设置').click();
    await page.getByText('个人设置', { exact: true }).waitFor({ timeout: 5000 });
  });

  let template;
  await step('模板库支持上传、列表和下载', async () => {
    const form = new FormData();
    form.append('name', `PW模板${suffix}`);
    form.append('description', 'Playwright 模板说明');
    form.append('content', 'Playwright 模板摘要');
    form.append('file', new Blob(['%PDF-1.4\nPlaywright template'], { type: 'application/pdf' }), `pw-template-${suffix}.pdf`);
    template = await apiForm('/contract-templates', { token: adminToken, form });
    const list = await api('/contract-templates/manage', { token: adminToken });
    if (!list.some(item => item.id === template.id && item.originalName === `pw-template-${suffix}.pdf`)) {
      throw new Error('模板管理列表缺少刚上传的文件模板');
    }
    const downloaded = await apiDownload(`/contract-templates/${template.id}/download`, adminToken);
    if (!downloaded.disposition.includes(`pw-template-${suffix}.pdf`) || downloaded.bytes.byteLength === 0) {
      throw new Error('模板下载响应缺少文件名或内容');
    }
    return template.originalName;
  });

  await step('起草页展示参考模板且不覆盖概述', async () => {
    const drafterLogin = await loginApi(drafter.username);
    await setSession(page, drafterLogin);
    await page.goto(`${WEB}/contracts/create`);
    await page.waitForLoadState('networkidle');
    const body = await page.locator('body').innerText();
    if (!body.includes('参考模板') || !body.includes(template.name)) {
      throw new Error('起草页未显示参考模板');
    }
    const textarea = page.locator('textarea').first();
    await textarea.fill('手工填写的合同概述');
    await page.getByRole('button', { name: /下载/ }).first().click();
    await page.waitForTimeout(300);
    if ((await textarea.inputValue()) !== '手工填写的合同概述') {
      throw new Error('模板下载不应覆盖合同概述');
    }
  });

  let contract;
  await step('API 完整合同流程可走通', async () => {
    const drafterLogin = await loginApi(drafter.username);
    contract = await api('/contracts', {
      method: 'POST',
      token: drafterLogin.token,
      body: {
        name: `PW全流程合同${suffix}`,
        customerId: customer.id,
        beginDate: '2026-06-12',
        endDate: '2027-06-12',
        content: 'Playwright 全流程合同概述'
      }
    });
    const assignerLogin = await loginApi(assigner.username);
    await api(`/contracts/${contract.id}/assign`, {
      method: 'POST',
      token: assignerLogin.token,
      body: {
        countersignUserIds: [countersigner.id],
        approvalUserIds: [approver.id],
        signUserId: signer.id
      }
    });
    await api(`/contracts/${contract.id}/countersign`, {
      method: 'POST',
      token: (await loginApi(countersigner.username)).token,
      body: { opinion: '会签通过' }
    });
    await api(`/contracts/${contract.id}/finalize`, {
      method: 'POST',
      token: drafterLogin.token,
      body: { content: 'Playwright 定稿概述' }
    });
    await api(`/contracts/${contract.id}/approve`, {
      method: 'POST',
      token: (await loginApi(approver.username)).token,
      body: { result: 'APPROVED', opinion: '审批通过' }
    });
    await api(`/contracts/${contract.id}/sign`, {
      method: 'POST',
      token: (await loginApi(signer.username)).token,
      body: { signInfo: 'Playwright 签订', signedDate: '2026-06-12' }
    });
    const detail = await api(`/contracts/${contract.id}`, { token: adminToken });
    if (detail.contract.status !== 'SIGNED') throw new Error(`最终状态不是 SIGNED: ${detail.contract.status}`);
    return detail.contract.contractNo;
  });

  await step('签订后详情页和回退模型可查看', async () => {
    await setSession(page, adminLogin);
    await page.goto(`${WEB}/contracts/${contract.id}?tab=rollback`);
    await page.waitForLoadState('networkidle');
    const text = await page.locator('body').innerText();
    if (!text.includes('已签订') || !text.includes('回退模型') || !text.includes('第 1 轮')) {
      throw new Error('详情页未显示签订结果或回退模型轮次');
    }
  });

  await step('客户分页详情、日志筛选和工作台状态跳转可用', async () => {
    await setSession(page, adminLogin);
    await page.goto(`${WEB}/customers`);
    await page.waitForLoadState('networkidle');
    await page.getByPlaceholder('搜索客户').fill(customer.name);
    await page.getByRole('button', { name: /搜索/ }).click();
    await page.getByRole('button', { name: /展开/ }).first().click();
    let text = await page.locator('body').innerText();
    if (!text.includes('Playwright银行') || !text.includes('622200000000') || !text.includes('客户详情展开测试')) {
      throw new Error('客户详情展开缺少扩展字段');
    }

    await page.goto(`${WEB}/system/logs`);
    await page.waitForLoadState('networkidle');
    await page.locator('select').selectOption('CONTRACT');
    await page.locator('input[type="date"]').first().fill('2026-01-01');
    await page.getByRole('button', { name: /筛选/ }).click();
    await page.waitForLoadState('networkidle');
    text = await page.locator('body').innerText();
    if (!text.includes('操作日志') || !text.includes('CONTRACT')) throw new Error('日志模块/时间筛选不可用');

    await page.goto(`${WEB}/dashboard`);
    await page.waitForLoadState('networkidle');
    await page.locator('.status-card').first().click();
    await page.waitForURL(/\/contracts\?status=/, { timeout: 5000 });
    text = await page.locator('body').innerText();
    if (!text.includes('合同管理')) throw new Error('工作台状态分布未跳转到合同管理');
  });

  await browser.close();

  if (consoleErrors.length) {
    console.log('\n[WARN] Browser console errors:');
    consoleErrors.forEach(err => console.log(`- ${err}`));
  }
  const failed = results.filter(r => !r.ok);
  if (failed.length) {
    process.exitCode = 1;
    console.error(`\n${failed.length} smoke step(s) failed.`);
  } else {
    console.log('\nAll smoke steps passed.');
  }
}

main().catch(error => {
  console.error(error);
  process.exit(1);
});
