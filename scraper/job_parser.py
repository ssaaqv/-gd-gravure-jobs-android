"""
广东塑料袋凹版印刷高精度招聘文本解析器
负责：地域锁定（广东21市+核心软包装镇）、行业相关性打分、噪音排除、薪资/班次/月休/机型/膜材/福利/电话精准提取。
"""
from __future__ import annotations

import hashlib
import re
from dataclasses import asdict, dataclass, field
from datetime import datetime
from typing import Dict, List, Optional, Tuple

# 广东 21 地市及核心塑料软包装/彩印产业镇区映射
GD_CITY_MAP: Dict[str, List[str]] = {
    "潮州(庵埠)": ["潮州", "庵埠", "潮安", "彩塘", "东凤", "龙湖", "饶平", "枫溪"],
    "汕头": ["汕头", "潮南", "潮阳", "峡山", "澄海", "龙湖区", "金平", "濠江"],
    "东莞": ["东莞", "长安", "厚街", "虎门", "塘厦", "凤岗", "常平", "寮步", "大朗", "清溪", "黄江", "桥头", "茶山", "石排", "企石", "东坑", "沙田", "麻涌"],
    "佛山": ["佛山", "顺德", "南海", "禅城", "三水", "高明", "勒流", "杏坛", "容桂", "大沥", "里水", "狮山"],
    "广州": ["广州", "白云", "增城", "花都", "番禺", "黄埔", "南沙", "从化", "天河", "海珠"],
    "深圳": ["深圳", "宝安", "龙岗", "龙华", "光明", "坪山", "沙井", "松岗", "公明", "观澜"],
    "中山": ["中山", "小榄", "古镇", "坦洲", "三乡", "黄圃", "南头", "东升", "火炬"],
    "惠州": ["惠州", "惠阳", "博罗", "惠东", "大亚湾", "仲恺"],
    "江门": ["江门", "鹤山", "新会", "开平", "台山", "恩平", "蓬江"],
    "揭阳": ["揭阳", "普宁", "揭东", "揭西", "惠来", "榕城"],
    "珠海": ["珠海", "斗门", "金湾", "香洲"],
    "肇庆": ["肇庆", "四会", "高要", "广宁", "德庆"],
    "清远": ["清远", "清城", "清新", "英德", "佛冈"],
    "汕尾": ["汕尾", "陆丰", "海丰"],
    "梅州": ["梅州", "兴宁", "梅县"],
    "河源": ["河源", "龙川", "东源"],
    "茂名": ["茂名", "高州", "化州", "电白"],
    "湛江": ["湛江", "廉江", "雷州"],
    "阳江": ["阳江", "阳春", "阳东"],
    "韶关": ["韶关", "乐昌", "南雄"],
    "云浮": ["云浮", "罗定", "新兴"],
}

NON_GD_PROVINCES = [
    "浙江", "杭州", "温州", "龙港", "苍南", "台州", "黄岩", "宁波", "义乌", "金华", "嘉兴", "绍兴",
    "江苏", "苏州", "昆山", "无锡", "常州", "南京", "南通", "扬州", "江阴", "张家港",
    "山东", "青岛", "临沂", "潍坊", "济南", "烟台", "淄博", "雄县", "河北", "保定", "沧州", "石家庄",
    "福建", "泉州", "晋江", "厦门", "福州", "漳州", "莆田",
    "河南", "郑州", "湖北", "武汉", "湖南", "长沙", "四川", "成都", "重庆",
    "安徽", "合肥", "桐城", "江西", "上海", "北京", "天津", "辽宁", "沈阳", "陕西", "西安", "广西",
]

# 正向行业关键词（塑料袋凹版印刷核心）
GRAVURE_STRONG_KEYWORDS = [
    "凹版", "凹印", "塑料印刷", "软包装", "彩印", "电子轴", "机械轴", "凹版印刷",
    "表印", "里印", "塑料袋", "包装袋", "背心袋", "复合袋", "自立袋", "拉链袋", "真空袋", "卫包",
]
GRAVURE_MEDIUM_KEYWORDS = [
    "印刷机长", "印刷师傅", "印刷副手", "印刷一助", "印刷二助", "印刷助手", "印刷学徒",
    "北人", "陕西北人", "欧诺", "华丰", "方邦", "博斯特", "松德", "中山松德",
    "PE", "OPP", "BOPP", "PET", "CPP", "PVC", "NY", "尼龙", "筒膜", "桶料", "套印", "调墨",
]
# 明确排除的噪音词（个人求职帖 或 纯其他无关印刷类型）
SEEKER_EXCLUDE_KEYWORDS = ["求职", "本人找工作", "找印刷工作", "求职印刷", "求职凹印", "想找一份"]
NON_GRAVURE_ONLY_KEYWORDS = ["丝印", "移印", "UV平板", "数码打印", "海德堡胶印", "瓦楞纸箱", "水印机"]


@dataclass
class JobPost:
    id: str
    title: str
    company: str
    city: str
    district_raw: str
    role: str                 # 机长 / 副手 / 学徒 / 调墨管理 / 其他
    salary_text: str
    salary_min: int           # 元/月，未知为 0
    salary_max: int           # 元/月，未知为 0
    salary_type: str          # 固定月薪 / 保底+计件 / 计件 / 面议
    shift: str                # 长白班 / 两班倒 / 未知
    work_days: str            # 如 28天 / 26天 / 月休2天 / 月休4天 / 未知
    machine_tags: List[str] = field(default_factory=list)
    material_tags: List[str] = field(default_factory=list)
    benefit_tags: List[str] = field(default_factory=list)
    phone: str = ""
    wechat: str = ""
    contact_name: str = ""
    raw_text: str = ""
    source_name: str = ""
    source_url: str = ""
    published_at: str = ""
    relevance_score: int = 0
    repeat_count: int = 1     # 同电话近90天重复招人次数（避坑指标）

    def to_dict(self) -> Dict:
        return asdict(self)


def detect_gd_city(text: str, location_hint: str = "") -> Tuple[Optional[str], str]:
    """返回 (标准广东城市名, 原始地名)；若判定为外省则返回 (None, '')。"""
    combined = f"{location_hint} {text}"

    # 先检查明确地名提示
    for city, keywords in GD_CITY_MAP.items():
        for kw in keywords:
            if kw in location_hint:
                return city, kw

    # 如果提示里明确是外省，直接排除
    for prov in NON_GD_PROVINCES:
        if prov in location_hint:
            return None, ""

    # 在全文中查找广东城市/产业镇
    for city, keywords in GD_CITY_MAP.items():
        for kw in keywords:
            if kw in combined:
                return city, kw

    if "广东" in combined or "珠三角" in combined or "粤东" in combined:
        return "广东(未细分)", "广东"

    return None, ""


def calculate_relevance(title: str, body: str) -> Tuple[int, bool]:
    """计算与“塑料袋凹版印刷企业招聘”的相关度分数，返回 (score, is_valid)。"""
    text = f"{title} {body}"
    text_upper = text.upper()

    # 1. 排除求职帖
    if any(kw in title for kw in SEEKER_EXCLUDE_KEYWORDS):
        return 0, False

    # 2. 如果只命中非凹印工艺且完全不含凹印/软包装词，则排除
    has_strong = any(kw.upper() in text_upper for kw in GRAVURE_STRONG_KEYWORDS)
    has_non_gravure = any(kw.upper() in text_upper for kw in NON_GRAVURE_ONLY_KEYWORDS)
    if has_non_gravure and not has_strong:
        return 0, False

    score = 0
    for kw in GRAVURE_STRONG_KEYWORDS:
        if kw.upper() in text_upper:
            score += 25
    for kw in GRAVURE_MEDIUM_KEYWORDS:
        if kw.upper() in text_upper:
            score += 12

    # 标题直接命中凹印/印刷再加权
    if any(k in title for k in ("凹印", "凹版", "印刷机长", "印刷师傅", "印刷副手", "印刷学徒")):
        score += 30

    return min(score, 100), score >= 25


def detect_role(text: str) -> str:
    if any(k in text for k in ("车间主任", "生产经理", "主管", "厂长", "调墨师傅", "调墨")):
        if not any(k in text for k in ("机长", "副手", "学徒")):
            return "调墨/管理"
    if any(k in text for k in ("机长", "开机师傅", "印刷师傅", "主操")):
        return "机长/师傅"
    if any(k in text for k in ("一助", "副手", "二助", "助手", "二手")):
        return "副手/助手"
    if any(k in text for k in ("学徒", "普工")):
        return "学徒"
    return "凹印相关"


def parse_salary(text: str) -> Tuple[str, int, int, str]:
    """精准解析月薪范围与计薪方式。"""
    salary_type = "面议"
    if "保底" in text and "计件" in text:
        salary_type = "保底+计件"
    elif "计件" in text:
        salary_type = "计件"

    # 模式1: 1.2~1.5万 / 1.2-1.8万
    m = re.search(r"(\d+(?:\.\d+)?)\s*[-~～至到]\s*(\d+(?:\.\d+)?)\s*万", text)
    if m:
        lo, hi = int(float(m.group(1)) * 10000), int(float(m.group(2)) * 10000)
        return f"{lo}-{hi}元", lo, hi, ("固定月薪" if salary_type == "面议" else salary_type)

    # 模式2: 12-20k / 9K~10K
    m = re.search(r"(\d+(?:\.\d+)?)\s*[kK千]?\s*[-~～至到]\s*(\d+(?:\.\d+)?)\s*[kK千]", text)
    if m:
        lo_val, hi_val = float(m.group(1)), float(m.group(2))
        lo = int(lo_val * 1000) if lo_val < 100 else int(lo_val)
        hi = int(hi_val * 1000) if hi_val < 100 else int(hi_val)
        if 2000 <= lo <= 60000 and lo <= hi <= 80000:
            return f"{lo}-{hi}元", lo, hi, ("固定月薪" if salary_type == "面议" else salary_type)

    # 模式3: 6000-7000元 / 10000~15000
    m = re.search(r"(\d{4,5})\s*[-~～至到]\s*(\d{4,5})\s*(?:元|块|/月)?", text)
    if m:
        lo, hi = int(m.group(1)), int(m.group(2))
        if 2500 <= lo <= 50000 and lo <= hi <= 80000:
            return f"{lo}-{hi}元", lo, hi, ("固定月薪" if salary_type == "面议" else salary_type)

    # 模式4: 单值 18000元 / 月薪12000 / 1.5万
    m = re.search(r"(?:月薪|工资|待遇|薪资|保底)[^\d]{0,6}(\d+(?:\.\d+)?)\s*万", text)
    if m:
        val = int(float(m.group(1)) * 10000)
        return f"约{val}元", val, val, ("固定月薪" if salary_type == "面议" else salary_type)

    m = re.search(r"(?:月薪|工资|待遇|薪资|保底)[^\d]{0,6}(\d{4,5})\s*(?:元|块|左右|以上)?", text)
    if m:
        val = int(m.group(1))
        if 2500 <= val <= 60000:
            return f"约{val}元", val, val, ("固定月薪" if salary_type == "面议" else salary_type)

    return "面议", 0, 0, salary_type


def parse_schedule(text: str) -> Tuple[str, str]:
    """解析班次与月工作天数/月休。"""
    shift = "未知"
    if any(k in text for k in ("长白班", "早八晚六", "早8晚6", "早八晚五", "纯白班", "白班")):
        if "两班" not in text and "夜班" not in text:
            shift = "长白班"
    if any(k in text for k in ("两班倒", "白夜班", "倒班", "12小时", "十二小时", "夜班")):
        shift = "两班倒"

    work_days = "未知"
    m_rest = re.search(r"月休\s*([一二三四五六两\d]+)\s*天", text)
    m_days = re.search(r"(2[4-9]|30)\s*天(?:制)?", text)
    if m_rest:
        work_days = f"月休{m_rest.group(1)}天"
    elif m_days:
        work_days = f"{m_days.group(1)}天制"
    elif "单休" in text:
        work_days = "单休(月休4天)"
    elif "双休" in text:
        work_days = "双休"
    elif "大小周" in text:
        work_days = "大小周"

    return shift, work_days


def extract_tags(text: str) -> Tuple[List[str], List[str], List[str]]:
    text_u = text.upper()
    machines: List[str] = []
    for label, kws in [
        ("电子轴", ["电子轴"]),
        ("机械轴", ["机械轴"]),
        ("高速机", ["高速机", "高速凹印", "高速电脑"]),
        ("陕西北人", ["北人"]),
        ("欧诺", ["欧诺"]),
        ("华丰", ["华丰"]),
        ("方邦", ["方邦"]),
        ("博斯特", ["博斯特"]),
        ("中山松德", ["松德"]),
    ]:
        if any(k in text for k in kws):
            machines.append(label)
    m_color = re.search(r"(\d{1,2})\s*色(?:机|凹印)?", text)
    if m_color:
        machines.append(f"{m_color.group(1)}色机")

    materials: List[str] = []
    for mat in ["PE", "OPP", "BOPP", "PET", "CPP", "PVC", "表印", "里印", "复合袋", "背心袋", "自立袋", "卫包", "筒膜", "桶料", "无溶剂"]:
        if mat in text_u or mat in text:
            materials.append(mat)

    benefits: List[str] = []
    if any(k in text for k in ("包吃包住", "包食宿", "食宿全包", "包吃住")):
        benefits.append("包吃住")
    elif "包吃" in text or "餐补" in text:
        benefits.append("包吃/餐补")
    if any(k in text for k in ("五险", "社保", "医保")):
        benefits.append("交社保")
    if "年终奖" in text:
        benefits.append("年终奖")
    if "水电全免" in text:
        benefits.append("免水电")
    if "全勤" in text:
        benefits.append("全勤奖")

    return machines, materials, benefits


def extract_contact(text: str) -> Tuple[str, str, str]:
    phone_m = re.search(r"(?<!\d)(1[3-9]\d{9})(?!\d)", text)
    phone = phone_m.group(1) if phone_m else ""

    wechat_m = re.search(r"(?:微信|VX|vx|V信)[号：:\s]*([a-zA-Z0-9_-]{5,20})", text)
    wechat = wechat_m.group(1) if wechat_m else ""
    if not wechat and "微信同号" in text and phone:
        wechat = phone

    name_m = re.search(r"([\u4e00-\u9fa5]{1,2}(?:总|经理|生|先生|女士|小姐|厂长|主任|师傅))", text)
    contact_name = name_m.group(1) if name_m else ""
    return phone, wechat, contact_name


def extract_company(text: str) -> str:
    m = re.search(r"([\u4e00-\u9fa5A-Za-z0-9]{2,20}(?:科技有限公司|包装有限公司|彩印有限公司|印刷有限公司|塑料有限公司|实业有限公司|彩印厂|印刷厂|包装厂))", text)
    return m.group(1) if m else "广东软包装/凹印企业"


def parse_job_record(
    title: str,
    body: str,
    location_hint: str = "",
    source_name: str = "手动/垂直源",
    source_url: str = "",
    published_at: str = "",
) -> Optional[JobPost]:
    """解析单条招聘记录；不符合广东塑料袋凹版印刷则返回 None。"""
    city, district_raw = detect_gd_city(f"{title} {body}", location_hint)
    if not city:
        return None

    score, valid = calculate_relevance(title, body)
    if not valid:
        return None

    full_text = f"{title}\n{body}".strip()
    role = detect_role(full_text)
    salary_text, salary_min, salary_max, salary_type = parse_salary(full_text)
    shift, work_days = parse_schedule(full_text)
    machines, materials, benefits = extract_tags(full_text)
    phone, wechat, contact_name = extract_contact(full_text)
    company = extract_company(full_text)

    if not published_at:
        published_at = datetime.now().strftime("%Y-%m-%d")

    uid_seed = f"{source_url}|{phone}|{title[:30]}|{city}"
    job_id = hashlib.md5(uid_seed.encode("utf-8")).hexdigest()[:12]

    return JobPost(
        id=job_id,
        title=title.strip()[:80],
        company=company,
        city=city,
        district_raw=district_raw,
        role=role,
        salary_text=salary_text,
        salary_min=salary_min,
        salary_max=salary_max,
        salary_type=salary_type,
        shift=shift,
        work_days=work_days,
        machine_tags=machines,
        material_tags=materials,
        benefit_tags=benefits,
        phone=phone,
        wechat=wechat,
        contact_name=contact_name,
        raw_text=body.strip()[:800],
        source_name=source_name,
        source_url=source_url,
        published_at=published_at,
        relevance_score=score,
    )
