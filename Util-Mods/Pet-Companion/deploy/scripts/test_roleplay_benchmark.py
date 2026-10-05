import json
from pathlib import Path
import tempfile
import unittest
import roleplay_benchmark as benchmark


class RoleplayBenchmarkTest(unittest.TestCase):
    def test_unparseable_or_malformed_rank_is_not_a_tie(self):
        self.assertEqual("unparsed", benchmark.ranking("The first response is better."))
        self.assertEqual("unparsed", benchmark.ranking("排序结果：模型1>模型1"))

    def test_both_orders_and_ties_parse(self):
        self.assertEqual("first", benchmark.ranking("排序结果：模型1>模型2\n理由：一致。"))
        self.assertEqual("second", benchmark.ranking("排序结果：模型2>模型1"))
        self.assertEqual("tie", benchmark.ranking("排序结果：模型1=模型2"))

    def test_proposal_keeps_ambiguity_and_evidence_instructions(self):
        system = "voice facts. Current/recent owner identification outranks other retrieved matches. old bridge example. Self-model INTEREST only: source rules."
        updated = benchmark.proposal_system(system)
        self.assertTrue(updated.startswith("voice facts. "))
        self.assertTrue(updated.endswith("Self-model INTEREST only: source rules."))
        self.assertIn("old bridge example", updated)
        self.assertEqual(system, updated.replace(benchmark.PROPOSAL, ""))

    def test_unknown_prompt_layout_fails_instead_of_silently_comparing_the_wrong_policy(self):
        with self.assertRaises(ValueError): benchmark.proposal_system("a changed prompt layout")

    def test_extract_refuses_non_synthetic_reports(self):
        with tempfile.TemporaryDirectory() as folder:
            report = Path(folder) / "report.md"
            report.write_text("private production report", encoding="utf-8")
            with self.assertRaises(ValueError): benchmark.extract(report, Path(folder) / "cases.json")

    def test_judge_receives_only_reply_action_attention_and_mode(self):
        rendered = json.loads(benchmark.judged_text(dict(reply="Yes.", action="SIT", attention="OWNER",
                conversation_mode="BRIEF", memory_candidate={"summary": "irrelevant to textual judging"})))
        self.assertEqual({"reply", "action", "attention", "conversation_mode"}, set(rendered))

    def test_failed_control_does_not_license_prompt_selection(self):
        rows = [dict(case_id="source-control", repetition=1, order=order, status="succeeded",
                     winner="tie", expected_winner="proposal")
                for order in (["baseline", "proposal"], ["proposal", "baseline"])]
        summary = benchmark.summarize_judgments(rows)
        self.assertFalse(summary["calibration_passed"])
        self.assertEqual({}, summary["agreed_pairs"])
        self.assertIn("Do not use", summary["interpretation"])
        for row in rows:
            row.update(case_id="quiet-control", winner="proposal")
        self.assertFalse(benchmark.summarize_judgments(rows)["calibration_passed"])

    def test_swapped_order_disagreement_and_truncation_are_not_pairwise_wins(self):
        rows = [dict(case_id="turn", repetition=1, order=["baseline", "proposal"],
                     status="succeeded", winner="proposal"),
                dict(case_id="turn", repetition=1, order=["proposal", "baseline"],
                     status="succeeded", winner="tie"),
                dict(case_id="truncated", repetition=1, order=["baseline", "proposal"],
                     status="truncated", winner="proposal")]
        summary = benchmark.summarize_judgments(rows)
        self.assertEqual(["turn/1"], summary["order_sensitive_pairs"])
        self.assertEqual(["truncated/1"], summary["incomplete_pairs"])
        self.assertEqual(1, summary["invalid_judgments"])
        self.assertEqual({}, summary["agreed_pairs"])


if __name__ == "__main__": unittest.main()
