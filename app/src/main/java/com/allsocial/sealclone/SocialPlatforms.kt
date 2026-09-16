package com.allsocial.sealclone

import androidx.compose.ui.graphics.Color

data class SocialPlatformInfo(
    val id: String,
    val name: String,
    val shortName: String,
    val brandColor: Color,
    val domainKeywords: List<String>,
    val badgeLabel: String,
    val sampleTip: String
)

object SocialPlatformRegistry {
    val platforms = listOf(
        SocialPlatformInfo(
            id = "youtube",
            name = "YouTube",
            shortName = "YT",
            brandColor = Color(0xFFFF0000),
            domainKeywords = listOf("youtube.com", "youtu.be"),
            badgeLabel = "4K / Shorts / MP3",
            sampleTip = "Supports 4K, 1080p, Shorts & high-bitrate 320k MP3 audio"
        ),
        SocialPlatformInfo(
            id = "instagram",
            name = "Instagram",
            shortName = "IG",
            brandColor = Color(0xFFE1306C),
            domainKeywords = listOf("instagram.com", "instagr.am"),
            badgeLabel = "Reels & Posts",
            sampleTip = "Download Instagram Reels, Video posts, and IGTV without watermark"
        ),
        SocialPlatformInfo(
            id = "tiktok",
            name = "TikTok",
            shortName = "TT",
            brandColor = Color(0xFF00F2FE),
            domainKeywords = listOf("tiktok.com", "douyin.com"),
            badgeLabel = "HD / No Watermark",
            sampleTip = "Download TikTok clips in original crisp HD quality without watermark"
        ),
        SocialPlatformInfo(
            id = "facebook",
            name = "Facebook",
            shortName = "FB",
            brandColor = Color(0xFF1877F2),
            domainKeywords = listOf("facebook.com", "fb.watch", "fb.com"),
            badgeLabel = "Watch & Reels",
            sampleTip = "Direct link download for Facebook Watch, Public Reels & Videos"
        ),
        SocialPlatformInfo(
            id = "twitter",
            name = "X (Twitter)",
            shortName = "X",
            brandColor = Color(0xFF1DA1F2),
            domainKeywords = listOf("twitter.com", "x.com"),
            badgeLabel = "Video & GIF",
            sampleTip = "Save Twitter/X status video clips and animated GIFs in top bitrate"
        ),
        SocialPlatformInfo(
            id = "reddit",
            name = "Reddit",
            shortName = "RD",
            brandColor = Color(0xFFFF4500),
            domainKeywords = listOf("reddit.com", "v.redd.it"),
            badgeLabel = "Combined Audio",
            sampleTip = "Downloads Reddit v.redd.it videos automatically merged with audio"
        ),
        SocialPlatformInfo(
            id = "pinterest",
            name = "Pinterest",
            shortName = "PIN",
            brandColor = Color(0xFFE60023),
            domainKeywords = listOf("pinterest.com", "pin.it"),
            badgeLabel = "Idea Pins & MP4",
            sampleTip = "Directly extracts Pinterest Idea Pins and video pins in original MP4"
        ),
        SocialPlatformInfo(
            id = "soundcloud",
            name = "SoundCloud",
            shortName = "SC",
            brandColor = Color(0xFFFF5500),
            domainKeywords = listOf("soundcloud.com"),
            badgeLabel = "HQ MP3 Music",
            sampleTip = "Extracts tracks and sets in maximum stream fidelity MP3 audio"
        ),
        SocialPlatformInfo(
            id = "spotify",
            name = "Spotify",
            shortName = "SPOT",
            brandColor = Color(0xFF1DB954),
            domainKeywords = listOf("spotify.com"),
            badgeLabel = "Audio Search",
            sampleTip = "Searches matching highest quality audio tracks for Spotify links"
        ),
        SocialPlatformInfo(
            id = "twitch",
            name = "Twitch",
            shortName = "TTV",
            brandColor = Color(0xFF9146FF),
            domainKeywords = listOf("twitch.tv"),
            badgeLabel = "Clips & VODs",
            sampleTip = "Downloads Twitch stream highlight clips and full VODs"
        ),
        SocialPlatformInfo(
            id = "vimeo",
            name = "Vimeo",
            shortName = "VM",
            brandColor = Color(0xFF1AB7EA),
            domainKeywords = listOf("vimeo.com"),
            badgeLabel = "1080p & 4K",
            sampleTip = "Full high quality Vimeo video download up to 4K resolution"
        ),
        SocialPlatformInfo(
            id = "bilibili",
            name = "Bilibili",
            shortName = "BILI",
            brandColor = Color(0xFF00A1D6),
            domainKeywords = listOf("bilibili.com"),
            badgeLabel = "Anime & HD",
            sampleTip = "Extracts Bilibili anime clips and video streams"
        )
    )
}
