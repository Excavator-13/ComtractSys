# ContractSys 结题答辩产物

## 产物

- `01-启动报告.docx`
- `02-关闭报告.docx`
- `03-需求分析报告.docx`
- `04-设计报告.docx`
- `05-测试报告.docx`
- `06-用户使用手册.docx`
- `合同管理系统结题答辩.pptx`
- `final-demo-record.mjs`
- `contractsys-final-demo.mp4`（运行录制脚本后生成）

## 生成命令

```powershell
$PY="C:\Users\anwea\.cache\codex-runtimes\codex-primary-runtime\dependencies\python\python.exe"
$NODE="C:\Users\anwea\.cache\codex-runtimes\codex-primary-runtime\dependencies\node\bin\node.exe"
& $PY output\final-defense\build_docs.py
& $NODE output\final-defense\build_final_ppt.mjs
```

## 录制 MP4

先启动演示环境：

```powershell
.\scripts\start-demo.ps1
```

再录制并转码：

```powershell
npx --yes --package playwright node output\final-defense\final-demo-record.mjs
```

可选环境变量：

```powershell
$env:WEB_URL="http://127.0.0.1:5173"
$env:API_URL="http://127.0.0.1:8080/api/v1"
```

生成后停止环境：

```powershell
.\scripts\stop-demo.ps1
```

## 姓名替换

当前未提供真实姓名，Word 和 PPT 中使用角色占位。提供姓名后，将 `待补充` 或角色页对应文案替换为真实姓名即可。
