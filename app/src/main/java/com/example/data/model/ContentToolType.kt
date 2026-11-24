package com.example.data.model

enum class ContentToolType(
    val id: String,
    val titleBn: String,
    val titleEn: String,
    val subtitleBn: String,
    val subtitleEn: String,
    val placeholderBn: String,
    val placeholderEn: String
) {
    YOUTUBE_TITLE(
        id = "yt_title",
        titleBn = "ইউটিউব টাইটেল",
        titleEn = "YouTube Title",
        subtitleBn = "আকর্ষণীয় ও ক্লিক-যোগ্য টাইটেল",
        subtitleEn = "Catchy, high-CTR video titles",
        placeholderBn = "যেমন: ঢাকার সেরা স্ট্রিট ফুড ভ্লগ",
        placeholderEn = "e.g. Dhaka street food tour vlog"
    ),
    DESCRIPTION_TAGS(
        id = "desc_tags",
        titleBn = "ডেসক্রিপশন ও ট্যাগ",
        titleEn = "Description + Tags",
        subtitleBn = "SEO-ফ্রেন্ডলি বিবরণ ও হ্যাশট্যাগ",
        subtitleEn = "SEO-friendly description and tags",
        placeholderBn = "ভিডিওর মূল বিষয় লিখুন...",
        placeholderEn = "Describe what your video covers..."
    ),
    FACEBOOK_CAPTION(
        id = "fb_caption",
        titleBn = "ফেসবুক ক্যাপশন",
        titleEn = "Facebook Caption",
        subtitleBn = "গল্পের মতো ও শেয়ারযোগ্য পোস্ট",
        subtitleEn = "Engaging and shareable post copy",
        placeholderBn = "ছবি বা পোস্টের বিষয় কী?",
        placeholderEn = "What is your Facebook post about?"
    ),
    INSTAGRAM_CAPTION(
        id = "ig_caption",
        titleBn = "ইনস্টাগ্রাম + হ্যাশট্যাগ",
        titleEn = "Instagram + Hashtags",
        subtitleBn = "ট্রেন্ডি ক্যাপশন ও রিলস ট্যাগ",
        subtitleEn = "Trendy caption with targeted hashtags",
        placeholderBn = "রিলস বা ছবির থিম লিখুন...",
        placeholderEn = "Describe your reel or photo..."
    ),
    SHORT_SCRIPT(
        id = "short_script",
        titleBn = "শর্ট ভিডিও স্ক্রিপ্ট",
        titleEn = "Short Video Script",
        subtitleBn = "৩০-৬০ সেকেন্ডের রিলস/শর্টস স্ক্রিপ্ট",
        subtitleEn = "30-60s hook, body & CTA script",
        placeholderBn = "কোন বিষয়ে ভিডিও বানাতে চান?",
        placeholderEn = "What topic is your short video about?"
    ),
    REPLY_WRITER(
        id = "reply_writer",
        titleBn = "রিপ্লাই / ইমেইল রাইটার",
        titleEn = "Reply / Email Writer",
        subtitleBn = "ভদ্র, দ্রুত ও স্মার্ট উত্তর তৈরি",
        subtitleEn = "Polite, clear comment or email reply",
        placeholderBn = "যে বার্তা বা কমেন্টের উত্তর দেবেন তা লিখুন...",
        placeholderEn = "Paste the message or comment to reply to..."
    ),
    TRANSLATOR(
        id = "translator",
        titleBn = "বাংলা ⇄ ইংরেজি অনুবাদক",
        titleEn = "Bengali ⇄ English",
        subtitleBn = "স্বাভাবিক ও প্রাঞ্জল অনুবাদ",
        subtitleEn = "Natural, idiomatic translation",
        placeholderBn = "অনুবাদ করার জন্য লেখাটি দিন...",
        placeholderEn = "Enter text to translate..."
    )
}

enum class ToolTone(val labelBn: String, val labelEn: String) {
    FRIENDLY("বন্ধুত্বপূর্ণ", "Friendly"),
    PROFESSIONAL("পেশাদার", "Professional"),
    FUNNY("মজার ও চটপটে", "Playful & Witty"),
    INSPIRATIONAL("অনুপ্রেরণামূলক", "Inspirational"),
    CONCISE("সরাসরি ও সংক্ষিপ্ত", "Direct & Short")
}

enum class ToolLength(val labelBn: String, val labelEn: String) {
    SHORT("ছোট", "Short"),
    MEDIUM("মাঝারি", "Medium"),
    LONG("বিস্তারিত", "Detailed")
}

enum class TranslationDirection(val labelBn: String, val labelEn: String) {
    BN_TO_EN("বাংলা → English", "Bengali → English"),
    EN_TO_BN("English → বাংলা", "English → Bengali")
}
