package eu.kanade.tachiyomi.animeextension.en.anistream

import android.app.Application
import android.content.SharedPreferences
import androidx.preference.EditTextPreference
import androidx.preference.PreferenceScreen
import eu.kanade.tachiyomi.animesource.ConfigurableAnimeSource
import eu.kanade.tachiyomi.animesource.model.AnimeFilterList
import eu.kanade.tachiyomi.animesource.model.AnimesPage
import eu.kanade.tachiyomi.animesource.model.SAnime
import eu.kanade.tachiyomi.animesource.model.SEpisode
import eu.kanade.tachiyomi.animesource.model.Video
import eu.kanade.tachiyomi.animesource.online.AnimeHttpSource
import eu.kanade.tachiyomi.network.GET
import okhttp3.Request
import okhttp3.Response
import org.json.JSONArray
import org.json.JSONObject
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

class AniStream : AnimeHttpSource(), ConfigurableAnimeSource {
    override val name = "AniStream"
    override val lang = "en"
    override val supportsLatest = false
    override val baseUrl: String get() = prefs.getString(BASE_URL_KEY, DEFAULT_URL)!!

    private val prefs: SharedPreferences by lazy {
        Injekt.get<Application>().getSharedPreferences("source_$id", 0)
    }

    // ── Popular ──
    override fun popularAnimeRequest(page: Int): Request =
        GET("$baseUrl/admin/chunklistjson", headers)

    override fun popularAnimeParse(response: Response): AnimesPage {
        val arr = JSONArray(response.body.string())
        val seen = LinkedHashSet<String>()
        for (i in 0 until arr.length()) seen.add(arr.getJSONObject(i).getString("title"))
        return AnimesPage(seen.map { t ->
            SAnime.create().apply { title = t; url = "/anime/$t"; thumbnail_url = "" }
        }, false)
    }

    // ── Latest (reuse popular) ──
    override fun latestUpdatesRequest(page: Int) = popularAnimeRequest(page)
    override fun latestUpdatesParse(response: Response) = popularAnimeParse(response)

    // ── Search ──
    override fun searchAnimeRequest(page: Int, query: String, filters: AnimeFilterList) =
        popularAnimeRequest(page)

    override fun searchAnimeParse(response: Response): AnimesPage {
        val p = popularAnimeParse(response)
        return AnimesPage(p.animes, false)
    }

    // ── Details ──
    override fun animeDetailsParse(response: Response): SAnime =
        SAnime.create().apply { title = "AniStream"; description = "Private library" }

    // ── Episodes ──
    override fun episodeListRequest(anime: SAnime): Request =
        GET("$baseUrl/admin/chunklistjson", headers)

    override fun episodeListParse(response: Response): List<SEpisode> {
        val arr = JSONArray(response.body.string())
        val out = mutableListOf<SEpisode>()
        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i)
            out.add(SEpisode.create().apply {
                name = "S${o.getString("season")} E${o.getInt("num")} - ${o.getString("title")}"
                url = "/ep/${o.getInt("id")}"
                episode_number = o.getInt("num").toFloat()
            })
        }
        return out.reversed()
    }

    // ── Videos ──
    override fun videoListRequest(episode: SEpisode): Request {
        val id = episode.url.removePrefix("/ep/")
        return GET("$baseUrl/stremio/stream/series/tge:$id.json", headers)
    }

    override fun videoListParse(response: Response): List<Video> {
        val obj = JSONObject(response.body.string())
        val streams = obj.getJSONArray("streams")
        val vids = mutableListOf<Video>()
        for (i in 0 until streams.length()) {
            val s = streams.getJSONObject(i)
            val url = s.getString("url")
            val label = s.optString("name", "Stream ${i + 1}")
            vids.add(Video(url, label, url))
        }
        return vids
    }

    // ── Settings ──
    override fun setupPreferenceScreen(screen: PreferenceScreen) {
        EditTextPreference(screen.context).apply {
            key = BASE_URL_KEY
            title = "Server URL"
            summary = baseUrl
            setDefaultValue(DEFAULT_URL)
        }.let(screen::addPreference)
    }

    companion object {
        private const val BASE_URL_KEY = "base_url"
        private const val DEFAULT_URL = "http://127.0.0.1:8765"
    }
}
