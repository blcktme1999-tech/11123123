package tw.cib.reader

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

data class MenuNode(val title: String, val target: String, val children: List<MenuNode>)

data class PageLink(val title: String, val meta: String, val image: String, val target: String)

data class PageDoc(
    val id: String,
    val title: String,
    val kind: String,
    val crumbs: List<String>,
    val items: List<PageLink>,
    val body: String,
    val images: List<String>
)

data class HomeTab(val title: String, val more: String, val items: List<PageLink>)

data class LabBlock(val title: String, val image: String, val more: String, val links: List<PageLink>)

data class HomePage(
    val logo: String,
    val banners: List<PageLink>,
    val news: List<HomeTab>,
    val wanted: List<HomeTab>,
    val services: List<PageLink>,
    val serviceLinks: List<PageLink>,
    val labs: List<LabBlock>
)

class Catalog(val menu: List<MenuNode>, val home: HomePage, val pages: Map<String, PageDoc>) {
    val titles: List<PageDoc> = pages.values.sortedBy { it.title }

    companion object {
        fun load(context: Context): Catalog {
            val text = context.assets.open("catalog.json").bufferedReader().use { it.readText() }
            val root = JSONObject(text)
            val pages = buildMap {
                val array = root.getJSONArray("pages")
                for (index in 0 until array.length()) {
                    val page = pageOf(array.getJSONObject(index))
                    put(page.id, page)
                }
            }
            return Catalog(menuOf(root.getJSONArray("menu")), homeOf(root.optJSONObject("home")), pages)
        }

        private fun menuOf(array: JSONArray): List<MenuNode> = List(array.length()) { index ->
            val item = array.getJSONObject(index)
            MenuNode(item.optString("title"), item.optString("target"), menuOf(item.optJSONArray("children") ?: JSONArray()))
        }

        private fun linkOf(item: JSONObject) = PageLink(
            item.optString("title"),
            item.optString("meta"),
            item.optString("image"),
            item.optString("target")
        )

        private fun linksOf(array: JSONArray?) = List(array?.length() ?: 0) { linkOf(array!!.getJSONObject(it)) }

        private fun tabOf(item: JSONObject) = HomeTab(item.optString("title"), item.optString("more"), linksOf(item.optJSONArray("items")))

        private fun homeOf(item: JSONObject?): HomePage {
            if (item == null) return HomePage("", emptyList(), emptyList(), emptyList(), emptyList(), emptyList(), emptyList())
            val news = item.optJSONArray("news")
            val wanted = item.optJSONArray("wanted")
            val labs = item.optJSONArray("labs")
            return HomePage(
                logo = item.optString("logo"),
                banners = linksOf(item.optJSONArray("banners")),
                news = List(news?.length() ?: 0) { tabOf(news!!.getJSONObject(it)) },
                wanted = List(wanted?.length() ?: 0) { tabOf(wanted!!.getJSONObject(it)) },
                services = linksOf(item.optJSONArray("services")),
                serviceLinks = linksOf(item.optJSONArray("serviceLinks")),
                labs = List(labs?.length() ?: 0) { index ->
                    val lab = labs!!.getJSONObject(index)
                    LabBlock(lab.optString("title"), lab.optString("image"), lab.optString("more"), linksOf(lab.optJSONArray("links")))
                }
            )
        }

        private fun pageOf(item: JSONObject): PageDoc {
            val crumbs = item.optJSONArray("crumbs") ?: JSONArray()
            val images = item.optJSONArray("images") ?: JSONArray()
            return PageDoc(
                id = item.getString("id"),
                title = item.optString("title"),
                kind = item.optString("kind"),
                crumbs = List(crumbs.length()) { crumbs.getString(it) },
                items = linksOf(item.optJSONArray("items")),
                body = item.optString("body"),
                images = List(images.length()) { images.getString(it) }
            )
        }
    }
}
