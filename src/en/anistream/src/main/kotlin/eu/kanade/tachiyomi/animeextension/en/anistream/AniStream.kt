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
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

class AniStream : AnimeHttpSource(), ConfigurableAnimeSource {
    override val name = "AniStream"
    override val lang = "en"
    override val supportsLatest = false
    override val baseUrl: String get() = prefs.getString("base_url", "http://127.0.0.1:8765")!!
    private val prefs: SharedPreferences by lazy { Injekt.get<Application>().getSharedPreferences("source_$id", 0) }

    override fun headersBuilder() = super.headersBuilder().add("User-Agent", "AniStream-Ext/1.0")

    override fun popularAnimeRequest(page: Int): Request = GET("$baseUrl/admin/chunklistjson", headers)
    override fun popularAnimeParse(response: Response): AnimesPage {
        val arr = JSONArray(response.body.string())
        val titles = LinkedHashSet<String>()
        for (i in 0 until arr.length()) titles.add(arr.getJSONObject(i).getString("title"))
        val animes = titles.map { t -> SAnime.create().apply { title = t; url = "anime:$t"; thumbnail_url = "" } }
        return AnimesPage(animes, false)
    }

    override fun latestUpdatesRequest(page: Int): Request = popularAnimeRequest(page)
    override fun latestUpdatesParse(response: Response): AnimesPage = popularAnimeParse(response)

    override fun searchAnimeRequest(page: Int, query: String, filters: AnimeFilterList): Request = popularAnimeRequest(page)
    override fun searchAnimeParse(response: Response): AnimesPage {
        val page = popularAnimeParse(response)
        return AnimesPage(page.animes.filter { it.title.contains(query, true) }, false)
    }

    override fun animeDetailsParse(response: Response): SAnime = SAnime.create().apply { title = "AniStream"; description = "Your private library" }

    override fun episodeListRequest(anime: SAnime): Request = GET("$baseUrl/admin/chunklistjson", headers)
    override fun episodeListParse(response: Response): List<SEpisode> {
        val title = ""
        val arr = JSONArray(response.body.string())
        val out = mutableListOf<SEpisode>()
        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i)
            out.add(SEpisode.create().apply {
                name = "S${o.getString("season")} E${o.getInt("num")} — ${o.getString("title")}"
                url = "ep:${o.getInt("id")}"
                episode_number = o.getInt("num").toFloat()
                date_upload = 0L
            })
        }
        return out.reversed()
    }

    override fun videoListRequest(episode: SEpisode): Request =
        GET("$baseUrl/stremio/stream/series/tge:${episode.url.removePrefix("ep:")}.json", headers)
    override fun videoListParse(response: Response): List<Video> {
        val json = org.json.JSONObject(response.body.string())
        val streams = json.getJSONArray("streams")
        val vids = mutableListOf<Video>()
        for (i in 0 until streams.length()) {
            val s = streams.getJSONObject(i)
            vids.add(Video(s.getString("url"), s.getString("name"), s.getString("url")))
        }
        return vids
    }

    override fun getFilterList(): AnimeFilterList = AnimeFilterList()

    override fun setupPreferenceScreen(screen: PreferenceScreen) {
        EditTextPreference(screen.context).apply {
            key = "base_url"; title = "Server URL"; summary = baseUrl
            setDefaultValue("http://127.0.0.1:8765")
            dialogTitle = "Server URL"
        }.let(screen::addPreference)
    }
}
