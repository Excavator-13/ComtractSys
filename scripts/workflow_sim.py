"""
合同工作流状态机预演器 / Contract workflow state-machine explorer.

把 ContractService 的真实流程语义建模为状态机，对所有操作序列做 BFS 穷举，
机器化检测：
  1. 局部死锁：活跃状态(ASSIGNED/COUNTERSIGNED/FINALIZED/APPROVED)下没有
     与状态匹配的 PENDING 任务，无人能推进。
  2. 全局卡死：某个从起点可达的状态，无法再走到 SIGNED（cancel 不算"完成"）。
  3. 重复活跃任务：同一(类型,处理人,轮次)出现多于一条 PENDING。

模型保真点（对照 backend/.../contract/ContractService.java）：
  - assign 时 COUNTERSIGN 建为 PENDING，APPROVAL/SIGN 建为 SUPERSEDED 占位行。
  - finalize 时 createPendingTasksFromAssignees(APPROVAL) 从占位行物化 PENDING。
  - approve 全通过时 createPendingTasksFromAssignees(SIGN) 物化 PENDING。
  - 退回(return)/恢复(resume)/撤回合同(recall)/重提(resubmit) 各自的克隆与轮次自增。
  - 撤回任务(withdraw) 的 ensureWithdrawAllowed 约束 + rewindStatusForWithdraw 封存下一环节。

抽象角色：drafter=D, countersigner=C1, approver=A1/A2, signer=S。
轮次上限 ROUND_CAP 用于保证状态空间有限。
"""
import sys
from collections import deque, Counter

try:
    sys.stdout.reconfigure(encoding="utf-8")
except Exception:
    pass

DRAFTER = "D"
COUNTERSIGNERS = ("C1",)
APPROVERS = ("A1",)
SIGNER = "S"
ROUND_CAP = 4

# 状态机阶段顺序，用于 withdraw 选择"最近完成"的任务
STAGE_ORDER = {"COUNTERSIGN": 1, "FINALIZE": 2, "APPROVAL": 3, "SIGN": 4}

ACTIVE_STATUSES = {"ASSIGNED", "COUNTERSIGNED", "FINALIZED", "APPROVED"}
STATUS_EXPECTED_TYPE = {
    "ASSIGNED": "COUNTERSIGN",
    "COUNTERSIGNED": "FINALIZE",
    "FINALIZED": "APPROVAL",
    "APPROVED": "SIGN",
}

# ---- 状态表示 ----
# state = (status, round, return_target, tasks)
# tasks = tuple(sorted (type, status, assignee, round)) —— 多重集，保留重复以检测 INV4


# 收集"重复物化 PENDING"的违例（在转移构建时检测，规范化前）
DUP_CREATIONS = []


def mk(status, rnd, rt, tasks):
    # frozenset 规范化：去掉行为等价的重复行(同 类型/状态/处理人/轮次)，避免占位行累积导致状态爆炸
    return (status, rnd, rt, tuple(sorted(set(tasks))))


def _add_pending(nt, ttype, assignee, rnd, ctx):
    """加一条 PENDING；若同键已有 PENDING 则记为违例(重复物化)。"""
    if any(t == (ttype, "PENDING", assignee, rnd) for t in nt):
        DUP_CREATIONS.append((ctx, ttype, assignee, rnd))
    nt.append((ttype, "PENDING", assignee, rnd))


def tlist(state):
    return list(state[3])


def pending(tasks, ttype, rnd):
    return [t for t in tasks if t[0] == ttype and t[1] == "PENDING" and t[3] == rnd]


def has_completed(tasks, ttype, rnd):
    return any(t[0] == ttype and t[1] in ("DONE", "REJECTED") and t[3] == rnd for t in tasks)


def supersede_pending_type(tasks, ttype, rnd):
    out = []
    for t in tasks:
        if t[0] == ttype and t[1] == "PENDING" and t[3] == rnd:
            out.append((t[0], "SUPERSEDED", t[2], t[3]))
        else:
            out.append(t)
    return out


def supersede_all_pending(tasks, rnd):
    out = []
    for t in tasks:
        if t[1] == "PENDING" and t[3] == rnd:
            out.append((t[0], "SUPERSEDED", t[2], t[3]))
        else:
            out.append(t)
    return out


def assignees_of_type_round(tasks, ttype, rnd):
    seen = []
    for t in tasks:
        if t[0] == ttype and t[3] == rnd and t[2] not in seen:
            seen.append(t[2])
    return seen


def successors(state):
    """yield (action_label, new_state) for every legal action."""
    status, rnd, rt, tasks = state
    tasks = list(tasks)

    # assign (DRAFT -> ASSIGNED)
    if status == "DRAFT":
        nt = list(tasks)
        for c in COUNTERSIGNERS:
            nt.append(("COUNTERSIGN", "PENDING", c, rnd))
        for a in APPROVERS:
            nt.append(("APPROVAL", "SUPERSEDED", a, rnd))   # 占位
        nt.append(("SIGN", "SUPERSEDED", SIGNER, rnd))      # 占位
        yield ("assign", mk("ASSIGNED", rnd, None, nt))

    # countersign (ASSIGNED)
    if status == "ASSIGNED":
        for c in COUNTERSIGNERS:
            if pending(tasks, "COUNTERSIGN", rnd) and any(t[2] == c for t in pending(tasks, "COUNTERSIGN", rnd)):
                nt = []
                done_one = False
                for t in tasks:
                    if not done_one and t == ("COUNTERSIGN", "PENDING", c, rnd):
                        nt.append(("COUNTERSIGN", "DONE", c, rnd)); done_one = True
                    else:
                        nt.append(t)
                if pending(nt, "COUNTERSIGN", rnd):
                    yield (f"countersign({c})", mk("ASSIGNED", rnd, None, nt))
                else:
                    nt.append(("FINALIZE", "PENDING", DRAFTER, rnd))
                    yield (f"countersign({c})->COUNTERSIGNED", mk("COUNTERSIGNED", rnd, None, nt))

    # finalize (COUNTERSIGNED)
    if status == "COUNTERSIGNED":
        if pending(tasks, "FINALIZE", rnd):
            nt = [(("FINALIZE", "DONE", t[2], t[3]) if t == ("FINALIZE", "PENDING", DRAFTER, rnd) else t) for t in tasks]
            for a in assignees_of_type_round(nt, "APPROVAL", rnd):
                _add_pending(nt, "APPROVAL", a, rnd, "finalize")
            yield ("finalize", mk("FINALIZED", rnd, None, nt))

    # approve (FINALIZED)
    if status == "FINALIZED":
        for a in APPROVERS:
            if any(t[2] == a for t in pending(tasks, "APPROVAL", rnd)):
                # APPROVED 分支
                nt = []
                done_one = False
                for t in tasks:
                    if not done_one and t == ("APPROVAL", "PENDING", a, rnd):
                        nt.append(("APPROVAL", "DONE", a, rnd)); done_one = True
                    else:
                        nt.append(t)
                if pending(nt, "APPROVAL", rnd):
                    yield (f"approve({a})", mk("FINALIZED", rnd, None, nt))
                else:
                    for s in assignees_of_type_round(nt, "SIGN", rnd):
                        _add_pending(nt, "SIGN", s, rnd, "approve->APPROVED")
                    yield (f"approve({a})->APPROVED", mk("APPROVED", rnd, None, nt))
                # REJECTED 分支
                nt2 = []
                done_one = False
                for t in tasks:
                    if not done_one and t == ("APPROVAL", "PENDING", a, rnd):
                        nt2.append(("APPROVAL", "REJECTED", a, rnd)); done_one = True
                    else:
                        nt2.append(t)
                nt2 = supersede_pending_type(nt2, "APPROVAL", rnd)
                yield (f"reject({a})", mk("REJECTED", rnd, None, nt2))

    # sign (APPROVED)
    if status == "APPROVED":
        if pending(tasks, "SIGN", rnd):
            nt = [(("SIGN", "DONE", t[2], t[3]) if t == ("SIGN", "PENDING", SIGNER, rnd) else t) for t in tasks]
            if pending(nt, "SIGN", rnd):
                yield ("sign", mk("APPROVED", rnd, None, nt))
            else:
                yield ("sign->SIGNED", mk("SIGNED", rnd, None, nt))

    # return (ASSIGNED/FINALIZED/APPROVED)
    if status in ("ASSIGNED", "FINALIZED", "APPROVED"):
        cur_type = {"ASSIGNED": "COUNTERSIGN", "FINALIZED": "APPROVAL", "APPROVED": "SIGN"}[status]
        for target in ("DRAFT", "FINALIZE"):
            if target == "FINALIZE" and status == "ASSIGNED":
                continue  # 会签阶段只能打回到重新起草
            actors = set(t[2] for t in pending(tasks, cur_type, rnd))
            for actor in actors:
                nt = []
                done_one = False
                for t in tasks:
                    if not done_one and t == (cur_type, "PENDING", actor, rnd):
                        nt.append((cur_type, "REJECTED", actor, rnd)); done_one = True
                    else:
                        nt.append(t)
                nt = supersede_all_pending(nt, rnd)
                yield (f"return({actor},{target})", mk("RETURNED", rnd, target, nt))

    # resume (RETURNED)
    if status == "RETURNED" and rnd < ROUND_CAP:
        nr = rnd + 1
        if rt == "DRAFT":
            nt = list(tasks)
            for c in assignees_of_type_round(tasks, "COUNTERSIGN", rnd):
                _add_pending(nt, "COUNTERSIGN", c, nr, "resume(DRAFT)")
            for a in assignees_of_type_round(tasks, "APPROVAL", rnd):
                nt.append(("APPROVAL", "SUPERSEDED", a, nr))
            for s in assignees_of_type_round(tasks, "SIGN", rnd):
                nt.append(("SIGN", "SUPERSEDED", s, nr))
            yield ("resume(DRAFT)", mk("ASSIGNED", nr, None, nt))
        elif rt == "FINALIZE":
            nt = list(tasks)
            nt.append(("FINALIZE", "PENDING", DRAFTER, nr))
            for c in assignees_of_type_round(tasks, "COUNTERSIGN", rnd):
                nt.append(("COUNTERSIGN", "SUPERSEDED", c, nr))
            for a in assignees_of_type_round(tasks, "APPROVAL", rnd):
                nt.append(("APPROVAL", "SUPERSEDED", a, nr))
            for s in assignees_of_type_round(tasks, "SIGN", rnd):
                nt.append(("SIGN", "SUPERSEDED", s, nr))
            yield ("resume(FINALIZE)", mk("COUNTERSIGNED", nr, None, nt))

    # recall (ASSIGNED, 无已完成会签)
    if status == "ASSIGNED" and not has_completed(tasks, "COUNTERSIGN", rnd) and rnd < ROUND_CAP:
        nt = supersede_all_pending(tasks, rnd)
        yield ("recall", mk("DRAFT", rnd + 1, None, nt))

    # resubmit (REJECTED)
    if status == "REJECTED" and rnd < ROUND_CAP:
        nr = rnd + 1
        nt = list(tasks)
        for c in assignees_of_type_round(tasks, "COUNTERSIGN", rnd):
            nt.append(("COUNTERSIGN", "SUPERSEDED", c, nr))
        for a in assignees_of_type_round(tasks, "APPROVAL", rnd):
            _add_pending(nt, "APPROVAL", a, nr, "resubmit")
        for s in assignees_of_type_round(tasks, "SIGN", rnd):
            nt.append(("SIGN", "SUPERSEDED", s, nr))
        yield ("resubmit", mk("FINALIZED", nr, None, nt))

    # withdrawTask
    for actor in [DRAFTER] + list(COUNTERSIGNERS) + list(APPROVERS) + [SIGNER]:
        cand = [t for t in tasks if t[2] == actor and t[1] in ("DONE", "REJECTED") and t[3] == rnd]
        if not cand:
            continue
        # 取阶段最靠后的（近似 max operatedAt）
        task = max(cand, key=lambda t: STAGE_ORDER.get(t[0], 0))
        ttype = task[0]
        # ensureWithdrawAllowed
        if ttype == "SIGN" or status == "SIGNED":
            continue
        if ttype == "COUNTERSIGN" and has_completed(tasks, "FINALIZE", rnd):
            continue
        if ttype == "FINALIZE" and has_completed(tasks, "APPROVAL", rnd):
            continue
        if ttype == "APPROVAL" and has_completed(tasks, "SIGN", rnd):
            continue
        # 复位该任务为 PENDING
        nt = []
        reset_one = False
        for t in tasks:
            if not reset_one and t == task:
                nt.append((ttype, "PENDING", actor, rnd)); reset_one = True
            else:
                nt.append(t)
        # rewind
        if ttype == "COUNTERSIGN":
            nt = supersede_pending_type(nt, "FINALIZE", rnd); ns = "ASSIGNED"
        elif ttype == "FINALIZE":
            nt = supersede_pending_type(nt, "APPROVAL", rnd); ns = "COUNTERSIGNED"
        elif ttype == "APPROVAL":
            nt = supersede_pending_type(nt, "SIGN", rnd); ns = "FINALIZED"
        else:
            continue
        yield (f"withdraw({actor},{ttype})", mk(ns, rnd, None, nt))


def explore():
    start = mk("DRAFT", 1, None, [])
    adj = {}            # state -> list of (label, succ)  (不含 cancel)
    q = deque([start])
    seen = {start}
    STATE_CAP = 200000
    while q:
        if len(seen) > STATE_CAP:
            print(f"!! 状态数超过上限 {STATE_CAP}，提前停止（可能模型未收敛）")
            break
        s = q.popleft()
        adj[s] = []
        for lbl, ns in successors(s):
            adj[s].append((lbl, ns))
            if ns not in seen:
                seen.add(ns); q.append(ns)
    return start, seen, adj


def reverse_reachable_to_signed(seen, adj):
    rev = {s: [] for s in seen}
    for s, edges in adj.items():
        for _, ns in edges:
            rev.setdefault(ns, [])
            rev[ns].append(s)
    signed = [s for s in seen if s[0] == "SIGNED"]
    R = set(signed)
    q = deque(signed)
    while q:
        s = q.popleft()
        for p in rev.get(s, []):
            if p not in R:
                R.add(p); q.append(p)
    return R


def shortest_path(start, target, adj):
    q = deque([start]); prev = {start: None}
    while q:
        s = q.popleft()
        if s == target:
            break
        for lbl, ns in adj.get(s, []):
            if ns not in prev:
                prev[ns] = (s, lbl); q.append(ns)
    if target not in prev:
        return None
    path = []
    cur = target
    while prev[cur] is not None:
        ps, lbl = prev[cur]
        path.append(lbl); cur = ps
    return list(reversed(path))


def main():
    start, seen, adj = explore()
    R = reverse_reachable_to_signed(seen, adj)

    print(f"可达状态总数: {len(seen)}  (轮次上限={ROUND_CAP}, 审批人={len(APPROVERS)})")
    print(f"可达 SIGNED 的状态数: {len(R)}")
    print("=" * 70)

    # 缺陷 1：局部死锁，按状态分类（区分 ASSIGNED 无会签 vs 其它）
    deadlocks = {}
    for s in seen:
        status, rnd, rt, tasks = s
        if status in ACTIVE_STATUSES:
            exp = STATUS_EXPECTED_TYPE[status]
            if not pending(tasks, exp, rnd):
                deadlocks.setdefault(status, []).append(s)
    total_dl = sum(len(v) for v in deadlocks.values())
    print(f"[检测1] 局部死锁（活跃状态但无匹配 PENDING 任务）: {total_dl} 个")
    for status, lst in deadlocks.items():
        # 找一个轮次最小的代表（证明不是 ROUND_CAP 假象）
        rep = min(lst, key=lambda s: (s[1], len(shortest_path(start, s, adj) or [99] * 99)))
        p = shortest_path(start, rep, adj)
        print(f"   ▶ {status} 缺 {STATUS_EXPECTED_TYPE[status]} 任务: {len(lst)} 个; 最小轮次={rep[1]}"
              f"{'（< 上限，非 CAP 假象）' if rep[1] < ROUND_CAP else ''}")
        print(f"     最短复现({len(p)}步): {' -> '.join(p)}")
        # 该状态还能做什么（是否能靠 recall 逃逸）
        outs = [lbl for lbl, _ in adj.get(rep, [])]
        print(f"     该状态可选动作: {outs if outs else '无（完全死锁）'}")

    # 缺陷 2：全局卡死，排除 round==ROUND_CAP 的上限假象
    stuck = [s for s in seen if s[0] not in ("SIGNED", "CANCELLED") and s not in R]
    genuine = [s for s in stuck if s[1] < ROUND_CAP]
    print(f"\n[检测2] 全局卡死（可达但无法再走到 SIGNED）: 共 {len(stuck)} 个，"
          f"其中非 CAP 假象(轮次<上限) {len(genuine)} 个")
    for s in genuine[:8]:
        p = shortest_path(start, s, adj)
        outs = [lbl for lbl, ns in adj.get(s, [])]
        print(f"   状态={s[0]} 轮次={s[1]} rt={s[2]}  可选动作={outs}")
        print(f"     复现: {' -> '.join(p) if p else '?'}")

    # 缺陷 3：重复活跃任务
    dup = []
    for s in seen:
        c = Counter((t[0], t[2], t[3]) for t in s[3] if t[1] == "PENDING")
        if any(v > 1 for v in c.values()):
            dup.append(s)
    print(f"\n[检测3] 重复 PENDING 任务（同一 类型/处理人/轮次 多条 PENDING）: {len(dup)} 个")
    for s in dup[:6]:
        p = shortest_path(start, s, adj)
        print(f"   状态={s[0]} 轮次={s[1]}  路径: {' -> '.join(p) if p else '?'}")
        print(f"      tasks={s[3]}")

    # 正向 sanity：能否走通一条完整 happy path
    signed = [s for s in seen if s[0] == "SIGNED"]
    print(f"\n[检测4] 至少一条可达 SIGNED 路径: {'是' if signed else '否'}")
    if signed:
        print("   样例 happy path:", " -> ".join(shortest_path(start, signed[0], adj)))

    print(f"\n[检测5] 重复物化 PENDING（转移构建时检出，规范化前）: {len(DUP_CREATIONS)} 处")
    for d in DUP_CREATIONS[:6]:
        print(f"   动作={d[0]} 重复创建 {d[1]} PENDING for {d[2]} @round{d[3]}")

    print("=" * 70)
    ok = not deadlocks and not genuine and not dup and not DUP_CREATIONS and signed
    print("结论:", "未发现死锁/卡死/重复任务缺陷 ✅" if ok else "发现潜在缺陷，见上 ⚠️")


if __name__ == "__main__":
    main()
