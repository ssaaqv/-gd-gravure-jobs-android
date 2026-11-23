import unittest
import sys
from pathlib import Path
sys.path.insert(0, str(Path(__file__).resolve().parent))
from ai_digest import generate_daily_digest
from job_parser import parse_job_record


class TestGuangdongGravureParser(unittest.TestCase):
    def test_guangzhou_deputy_and_apprentice(self):
        post = parse_job_record(
            title="招聘凹版印刷副手及学徒多名",
            body="凹版印刷副手及学徒多名，两班倒，28天制，包吃包住，交五险，月薪7000-10000元，有意者电话联系刘总13527683880。广州市美斯乐科技有限公司",
            location_hint="广东广州",
            source_name="天下软包装",
            source_url="http://www.txrbz.com/info/11068.html",
            published_at="2026-02-27",
        )
        self.assertIsNotNone(post)
        self.assertEqual(post.city, "广州")
        self.assertEqual(post.role, "副手/助手")
        self.assertEqual(post.salary_min, 7000)
        self.assertEqual(post.salary_max, 10000)
        self.assertEqual(post.shift, "两班倒")
        self.assertEqual(post.work_days, "28天制")
        self.assertEqual(post.phone, "13527683880")
        self.assertIn("包吃住", post.benefit_tags)
        self.assertIn("交社保", post.benefit_tags)

    def test_chaozhou_anbu_captain_electronic_shaft(self):
        post = parse_job_record(
            title="急招电子轴凹版印刷机长2名",
            body="潮安庵埠大型软包装彩印厂，10色陕西北人高速电子轴机，做OPP/PE复合袋、自立袋，月薪1.5-1.8万，保底+计件，月休两天，包食宿、年终奖，联系陈生13800138000微信同号",
            location_hint="",
            source_name="天下软包装",
            published_at="2026-11-20",
        )
        self.assertIsNotNone(post)
        self.assertEqual(post.city, "潮州(庵埠)")
        self.assertEqual(post.role, "机长/师傅")
        self.assertEqual(post.salary_min, 15000)
        self.assertEqual(post.salary_max, 18000)
        self.assertEqual(post.salary_type, "保底+计件")
        self.assertEqual(post.work_days, "月休两天")
        self.assertIn("电子轴", post.machine_tags)
        self.assertIn("陕西北人", post.machine_tags)
        self.assertIn("10色机", post.machine_tags)
        self.assertIn("复合袋", post.material_tags)
        self.assertEqual(post.wechat, "13800138000")

    def test_dongguan_day_shift_filter(self):
        post = parse_job_record(
            title="东莞厚街塑料袋彩印副手（早八晚六长白班）",
            body="招塑料袋凹印副手，长白班早八晚六，单休，月薪8-10K，包吃，联系王经理13912345678",
            location_hint="广东东莞",
            published_at="2026-11-21",
        )
        self.assertIsNotNone(post)
        self.assertEqual(post.city, "东莞")
        self.assertEqual(post.shift, "长白班")
        self.assertEqual(post.work_days, "单休(月休4天)")
        self.assertEqual(post.salary_min, 8000)
        self.assertEqual(post.salary_max, 10000)

    def test_exclude_non_guangdong(self):
        post = parse_job_record(
            title="招凹版印刷学徒6000-7000元，28天",
            body="招凹版印刷学徒6000-7000元，28天，交社保，食宿全包。浙江杭州",
            location_hint="浙江杭州",
        )
        self.assertIsNone(post)

    def test_exclude_job_seeker_post(self):
        post = parse_job_record(
            title="求职凹版印刷机长，10年经验，东莞佛山均可",
            body="本人10年凹印机长经验，求职广东塑料袋彩印厂",
            location_hint="广东东莞",
        )
        self.assertIsNone(post)

    def test_exclude_unrelated_screen_printing(self):
        post = parse_job_record(
            title="东莞招聘丝印师傅",
            body="熟练操作丝印移印机台，长白班6000元",
            location_hint="广东东莞",
        )
        self.assertIsNone(post)


    def test_ai_digest_local_fallback(self):
        post = parse_job_record(
            title="急招电子轴凹版印刷机长2名",
            body="潮安庵埠大型软包装厂，10色陕西北人高速电子轴机，月薪1.5-1.8万，包吃住交五险，联系陈生13800138000",
            location_hint="广东潮州",
            published_at="2026-11-23",
        )
        digest = generate_daily_digest([post.to_dict()])
        self.assertIn("潮州(庵埠)", digest["summary"])
        self.assertIn("15000-18000元", digest["summary"])


if __name__ == "__main__":
    unittest.main()
