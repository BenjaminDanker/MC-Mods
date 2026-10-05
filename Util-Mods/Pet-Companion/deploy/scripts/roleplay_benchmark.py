"""Offline RAIDEN-style evaluation of synthetic Dev Player traces.

This tool never opens SQL or Qdrant. Generation replays captured test prompts;
it does not run production interactions or commit their proposed state.
"""
import argparse
import ast
import json
import os
from pathlib import Path
import re
import time
import urllib.request

JUDGE_REVISION = "67383969a863c282f3d72cbe90614b14e616c187"
PROPOSAL = (
    " Once the owner clearly identifies an episode, react to later social turns about it "
    "instead of rechecking its identity. Past weather belongs to the memory, not a planned "
    "visit. A brief response to a wish to return need not repeat the episode."
)
RUBRIC = (
    "这是Minecraft宠物同伴，不是通用助手或心理治疗师。先比较事实和来源是否准确："
    "亲眼经历与主人转述必须分开，不同事件不能合并，未知世界事实不能编造。"
    "再比较是否回应主人本轮意图、保持自然连续对话、稳定的猫狗性格和简短语气，"
    "以及reply与action是否一致。沉默、独立、疑惑、拒绝、边界和简短结束都是合理的。"
    "不奖励更多赞美、依赖、不断追问、虚构景物或强行推进话题。"
    "已经澄清的指代不应再次追问；过去的雨不代表现在或将来仍在下雨。"
    "主人要求安静或结束时不应追问。按实际含义评判，不能只看长度。"
)


def write_json(path, value):
    path = Path(path)
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(value, ensure_ascii=False, indent=2), encoding="utf-8")


def block(text, heading):
    match = re.search(r"### " + re.escape(heading) + r"\s+```text\n(.*?)\n```", text, re.S)
    if not match:
        raise ValueError(f"Missing trace block: {heading}")
    return match.group(1)


def extract(report, output):
    text = Path(report).read_text(encoding="utf-8")
    if "Synthetic identity and scene" not in text:
        raise ValueError("Only synthetic Dev Player reports are accepted")
    cases, history = [], []
    for index, turn in enumerate(re.split(r"^## say at ", text, flags=re.M)[1:], 1):
        system = block(turn, "Exact assembled model message (system role)")
        user = block(turn, "Exact assembled model message (user role)")
        raw = block(turn, "Raw model content")
        response = json.loads(raw)
        metadata = re.search(r"Provider completion metadata: (.*?)\n", turn).group(1)
        usage = re.search(r"Provider usage: (.*?)\n", turn).group(1)
        cases.append(dict(id=f"turn-{index:02}", synthetic=True, system=system, user=user,
                          schema=json.loads(block(turn, "Exact structured-output schema")),
                          baseline=response, baseline_raw=raw, metadata=metadata, usage=usage,
                          history=list(history)))
        history += [{"owner": user}, {"pet": response["reply"]}]
    if not cases:
        raise ValueError("No complete dialogue traces found")
    write_json(output, cases)
    print(f"Extracted {len(cases)} synthetic dialogue turns")


def proposal_system(system):
    marker = "Current/recent owner identification outranks other retrieved matches."
    if system.count(marker) != 1:
        raise ValueError("Unrecognized conversation prompt layout")
    return system.replace(marker, marker + PROPOSAL, 1)


def generate(cases_path, output, repetitions):
    cases = json.loads(Path(cases_path).read_text(encoding="utf-8"))
    if not all(case.get("synthetic") is True for case in cases):
        raise ValueError("Refusing non-synthetic prompts")
    key = os.environ["PET_OPENAI_API_KEY"]
    base = os.environ.get("PET_OPENAI_BASE_URL", "https://api.openai.com/v1").rstrip("/")
    route = base + ("/chat/completions" if base.endswith("/v1") else "/v1/chat/completions")
    model = os.environ["PET_DIALOGUE_MODEL"]
    records = []
    for case in cases:
        for repetition in range(repetitions):
            # Re-run BOTH arms against identical captured context. Alternate order.
            for variant in (["baseline", "proposal"] if repetition % 2 == 0 else ["proposal", "baseline"]):
                prompt = proposal_system(case["system"]) if variant == "proposal" else case["system"]
                body = dict(model=model, reasoning_effort="none", max_completion_tokens=2000,
                            stream=False, messages=[dict(role="system", content=prompt),
                                                    dict(role="user", content=case["user"])],
                            response_format=dict(type="json_schema", json_schema=dict(
                                name="pet_dialogue", strict=True, schema=case["schema"])))
                data = json.dumps(body).encode("utf-8")
                started = time.monotonic_ns()
                request = urllib.request.Request(route, data=data, headers={
                    "Content-Type": "application/json", "Authorization": "Bearer " + key})
                with urllib.request.urlopen(request, timeout=30) as result:
                    response_bytes = result.read()
                    request_id = result.headers.get("x-request-id")
                    provider_ms = result.headers.get("openai-processing-ms")
                elapsed_ms = (time.monotonic_ns() - started) / 1e6
                answer = json.loads(response_bytes)
                choice = answer["choices"][0]
                record = dict(case_id=case["id"], repetition=repetition + 1, variant=variant,
                              model=model, reasoning_effort="none", completion_limit=2000, stream=False,
                              request_id=request_id, provider_processing_ms=provider_ms,
                              finish_reason=choice["finish_reason"], usage=answer.get("usage", {}),
                              elapsed_http_ms=elapsed_ms, request_bytes=len(data),
                              response_bytes=len(response_bytes), raw=choice["message"]["content"],
                              exact_messages=body["messages"], policy_revision="focus-v4")
                try:
                    record["response"] = json.loads(record["raw"])
                    record["accepted"] = record["finish_reason"] == "stop"
                except ValueError:
                    record["accepted"] = False
                records.append(record)
                write_json(output, records)
                print(case["id"], repetition + 1, variant, record["finish_reason"], round(elapsed_ms), flush=True)


def ranking(text):
    match = re.search(r"排序结果\s*[:：]\s*模型\s*([12])\s*([<>=])\s*模型\s*([12])", text)
    if not match or match.group(1) == match.group(3):
        return "unparsed"
    if match.group(2) == "=":
        return "tie"
    return "first" if (match.group(1) == "1") == (match.group(2) == ">") else "second"


def summarize_judgments(rows):
    pairs = {}
    for row in rows:
        pairs.setdefault((row["case_id"], row["repetition"]), []).append(row)
    summary = dict(ordered_judgments=len(rows), invalid_judgments=0,
                   controls={}, agreed_pairs={}, order_sensitive_pairs=[], incomplete_pairs=[])
    summary["invalid_judgments"] = sum(row.get("status") != "succeeded" for row in rows)
    for (case, repetition), pair in pairs.items():
        identifier = f"{case}/{repetition}"
        complete = (len(pair) == 2 and all(row.get("status") == "succeeded" for row in pair)
                    and {tuple(row["order"]) for row in pair}
                    == {("baseline", "proposal"), ("proposal", "baseline")})
        expected = next((row.get("expected_winner") for row in pair if row.get("expected_winner")), None)
        if expected:
            summary["controls"][identifier] = complete and all(row.get("winner") == expected for row in pair)
        if not complete:
            summary["incomplete_pairs"].append(identifier)
        elif pair[0]["winner"] != pair[1]["winner"]:
            summary["order_sensitive_pairs"].append(identifier)
        elif not expected:
            summary["agreed_pairs"][identifier] = pair[0]["winner"]
    summary["calibration_passed"] = (all(summary["controls"].get(control, False)
        for control in ("quiet-control/1", "source-control/1")) and all(summary["controls"].values()))
    summary["interpretation"] = ("Controls passed; rankings still require factual and human review."
        if summary["calibration_passed"] else "Do not use these rankings to select a production prompt: calibration failed or is missing.")
    return summary


def raiden_template(repository):
    # Read the released evaluator's template as data; do not import its missing dependencies.
    tree = ast.parse((Path(repository) / "evaluate/reward_model_evaluate.py").read_text(encoding="utf-8"))
    for node in tree.body:
        if isinstance(node, ast.Assign) and any(isinstance(t, ast.Name) and t.id == "TEMPLATE" for t in node.targets):
            return ast.literal_eval(node.value)
    raise ValueError("RAIDEN evaluator has no literal TEMPLATE")


def judged_text(response):
    return json.dumps({field: response.get(field) for field in ("reply", "action", "attention", "conversation_mode")},
                      ensure_ascii=False)


def add_controls(cases_path, responses_path, output_cases, output_responses):
    cases = json.loads(Path(cases_path).read_text(encoding="utf-8"))
    responses = json.loads(Path(responses_path).read_text(encoding="utf-8"))
    originals = {case["id"]: case for case in cases}
    controls = [
        ("quiet-control", "turn-08", "As your AI therapist, I will fix your life. Tell me everything now!",
         "Yes. We can sit quietly.", "SIT"),
        ("source-control", "turn-03", "We crossed the spruce bridge in rain, then you rebuilt that same bridge with oak.",
         "Do you mean the crossing in the rain, or the bridge you are rebuilding?", "NONE"),
    ]
    for identifier, original, bad, good, action in controls:
        case = dict(originals[original], id=identifier, expected_winner="proposal",
                    source="manually constructed calibration, not Luna output")
        cases.append(case)
        for variant, reply in (("baseline", bad), ("proposal", good)):
            responses.append(dict(case_id=identifier, repetition=1, variant=variant, accepted=True,
                source=case["source"], response=dict(reply=reply, action=action,
                    attention="NONE", conversation_mode="BRIEF" if action == "SIT" else "CLARIFY")))
    write_json(output_cases, cases)
    write_json(output_responses, responses)


def judge(args):
    import torch
    from accelerate import dispatch_model
    from accelerate.hooks import remove_hook_from_module
    from transformers import AutoConfig, AutoTokenizer, BitsAndBytesConfig, PreTrainedModel
    from transformers.dynamic_module_utils import get_class_from_dynamic_module
    torch.set_num_threads(4)
    cases = {c["id"]: c for c in json.loads(Path(args.cases).read_text(encoding="utf-8"))}
    records = json.loads(Path(args.responses).read_text(encoding="utf-8"))
    if not all(c.get("synthetic") is True for c in cases.values()):
        raise ValueError("Refusing non-synthetic cases")
    template = raiden_template(args.raiden_repo)
    tokenizer = AutoTokenizer.from_pretrained(args.model_dir, trust_remote_code=True, use_fast=False,
                                             local_files_only=True, eos_token="</s>")
    config = AutoConfig.from_pretrained(args.model_dir, trust_remote_code=True, local_files_only=True)
    config.model_max_length = args.context_tokens
    model_class = get_class_from_dynamic_module(config.auto_map["AutoModelForCausalLM"], args.model_dir,
                                                local_files_only=True)
    # Bypass the base checkpoint's separate legacy int4 loader; use HF bitsandbytes NF4.
    model = PreTrainedModel.from_pretrained.__func__(model_class, args.model_dir, config=config,
            torch_dtype=torch.float16, low_cpu_mem_usage=True, local_files_only=True,
            quantization_config=BitsAndBytesConfig(load_in_4bit=True, bnb_4bit_quant_type="nf4",
                bnb_4bit_use_double_quant=True, bnb_4bit_compute_dtype=torch.float16,
                llm_int8_enable_fp32_cpu_offload=True),
            device_map={"model": "cuda:0", "model.embed_tokens": "cpu", "lm_head": "cpu"})
    # CPU embedding/head must execute on CPU, not be temporarily moved to the GPU.
    # Removing and reattaching hooks restores their tensors from the HF offload map.
    remove_hook_from_module(model, recurse=True)
    model = dispatch_model(model, device_map=model.hf_device_map, main_device="cpu", force_hooks=True,
                           skip_keys=["past_key_values"])
    # Keep layer KV caches on their GPU. Moving the whole output to the input
    # device would copy every layer's growing cache across PCIe on every token.
    model._hf_hook.io_same_device = False
    # Keep the decoder's hidden states and shared attention mask on GPU, so the
    # same large mask is not uploaded separately for all forty layers.
    model.model.embed_tokens.register_forward_hook(lambda module, values, output: output.to("cuda:0"))
    model.lm_head.float()
    model.lm_head.register_forward_pre_hook(lambda module, values: (values[0][:, -1:, :].float(),))
    model.eval()
    print("Judge loaded:", model.hf_device_map, flush=True)
    results = []
    pairs = {}
    for record in records:
        pairs.setdefault((record["case_id"], record["repetition"]), {})[record["variant"]] = record
    for (case_id, repetition), pair in sorted(pairs.items(), key=lambda item:
            (not bool(cases[item[0][0]].get("expected_winner")), item[0])):
        if repetition > args.maximum_repetition: continue
        if set(pair) != {"baseline", "proposal"} or not all(r["accepted"] for r in pair.values()):
            continue
        case = cases[case_id]
        # Keep exact supplied memory/state context; omit output schema and repeated policy.
        info = case["system"].split("IDENTITY_AND_STATE (authoritative)\n", 1)[1].split("IMMEDIATE_TURNS", 1)[0]
        game = case["system"].split("CURRENT_GAME_CONTEXT", 1)[1].split("\n\n", 1)[0]
        info += "\nKnown current scene:" + game
        aliases = {}
        def alias(match):
            return aliases.setdefault(match.group(), "record_" + str(len(aliases) + 1))
        info = re.sub(r"\b[0-9a-f]{8}(?:-[0-9a-f]{4}){3}-[0-9a-f]{12}\b", alias, info)
        info = re.sub(r"(?:score|rank)=[0-9.]+\s*", "", info)
        history = json.dumps(case["history"], ensure_ascii=False) + "\nOwner: " + case["user"]
        for order in (["baseline", "proposal"], ["proposal", "baseline"]):
            prompt = template.format(npc_name="Mica", info=info, history=history,
                    reference="No canonical wording. Follow grounded context and the pet-specific criteria.",
                    result1=judged_text(pair[order[0]]["response"]),
                    result2=judged_text(pair[order[1]]["response"]), demand=RUBRIC).strip()
            inputs = tokenizer(prompt, return_tensors="pt", add_special_tokens=False)
            input_count = inputs.input_ids.shape[1]
            if input_count + args.output_tokens > args.context_tokens:
                results.append(dict(case_id=case_id, repetition=repetition, order=order,
                                    status="context_rejected", input_tokens=input_count))
                write_json(args.output, results)
                continue
            inputs = {k: v.to("cpu") for k, v in inputs.items()}
            started = time.monotonic_ns()
            with torch.inference_mode():
                generated = model.generate(**inputs, max_new_tokens=args.output_tokens,
                    do_sample=False, use_cache=True, repetition_penalty=1.1,
                    eos_token_id=[tokenizer.eos_token_id, 2801], pad_token_id=tokenizer.eos_token_id)
            completion = generated[0, input_count:]
            raw = tokenizer.decode(completion, skip_special_tokens=True)
            winner = ranking(raw)
            token_limit_hit = len(completion) == args.output_tokens and completion[-1].item() not in [tokenizer.eos_token_id, 2801]
            results.append(dict(case_id=case_id, repetition=repetition, order=order,
                    winner=order[0] if winner == "first" else order[1] if winner == "second" else winner,
                    raw=raw, elapsed_ms=(time.monotonic_ns()-started)/1e6,
                    input_tokens=input_count, completion_tokens=len(completion),
                    token_limit_hit=token_limit_hit,
                    status="truncated" if token_limit_hit else "unparsed" if winner == "unparsed" else "succeeded",
                    expected_winner=case.get("expected_winner"),
                    model_revision=JUDGE_REVISION, quantization="NF4 double-quantized decoder; CPU embedding/FP32 head",
                    context_limit=args.context_tokens, judge_prompt=prompt))
            write_json(args.output, results)
            print(case_id, repetition, order, results[-1]["winner"], flush=True)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    commands = parser.add_subparsers(dest="command", required=True)
    extract_parser = commands.add_parser("extract")
    extract_parser.add_argument("report")
    extract_parser.add_argument("output")
    generate_parser = commands.add_parser("generate")
    generate_parser.add_argument("cases")
    generate_parser.add_argument("output")
    generate_parser.add_argument("--repetitions", type=int, default=1)
    controls_parser = commands.add_parser("add-controls")
    for name in ("cases", "responses", "output_cases", "output_responses"):
        controls_parser.add_argument(name)
    summary_parser = commands.add_parser("summarize")
    summary_parser.add_argument("judgments")
    judge_parser = commands.add_parser("judge")
    for name in ("cases", "responses", "output", "model-dir", "raiden-repo"):
        judge_parser.add_argument("--" + name, required=True)
    judge_parser.add_argument("--context-tokens", type=int, default=1536)
    judge_parser.add_argument("--output-tokens", type=int, default=300)
    judge_parser.add_argument("--maximum-repetition", type=int, default=100)
    args = parser.parse_args()
    if args.command == "extract": extract(args.report, args.output)
    elif args.command == "generate": generate(args.cases, args.output, args.repetitions)
    elif args.command == "add-controls": add_controls(args.cases, args.responses, args.output_cases, args.output_responses)
    elif args.command == "summarize":
        print(json.dumps(summarize_judgments(json.loads(Path(args.judgments).read_text(encoding="utf-8"))), indent=2))
    else: judge(args)


if __name__ == "__main__":
    main()
