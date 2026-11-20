package tw.cib.reader

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.view.WindowManager
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.rememberAsyncImagePainter
import kotlinx.coroutines.launch
import org.json.JSONObject

private val Navy = Color(0xFF0E3A66)
private val Gold = Color(0xFFC6A15B)
private val Paper = Color(0xFFF4F6F8)
private val DateRed = Color(0xFF9D1C2C)

private data class FeatureTile(val title: String, val target: String, val icon: Int)

private val featureTiles = listOf(
    FeatureTile("案件公文", "route:caseQuery", R.drawable.feature_document),
    FeatureTile("案件查詢", "route:caseSearch", R.drawable.feature_search),
    FeatureTile("優先調查申請表", "route:priority", R.drawable.feature_form),
    FeatureTile("報案及申訴", "route:citizen", R.drawable.feature_report)
)

private sealed interface Screen {
    data object Home : Screen
    data class Page(val id: String) : Screen
    data object CaseQuery : Screen
    data object CaseSearch : Screen
    data object Priority : Screen
    data object Citizen : Screen
    data object Staff : Screen
    data class Chat(val caseId: String, val title: String, val staff: Boolean) : Screen

    fun isPrivate(): Boolean = this is CaseQuery || this is CaseSearch || this is Priority || this is Citizen || this is Staff || this is Chat
}

@Composable
fun CibApp(catalog: Catalog, api: ApiClient) {
    val stack = remember { androidx.compose.runtime.mutableStateListOf<Screen>(Screen.Home) }
    val context = LocalContext.current
    val activity = context as Activity
    val scope = rememberCoroutineScope()
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    var searching by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }
    var staffOn by remember { mutableStateOf(false) }
    val screen = stack.last()
    val hits = if (query.isBlank()) emptyList() else catalog.titles.filter { it.title.contains(query, true) }.take(40)

    DisposableEffect(screen) {
        if (screen.isPrivate()) activity.window.setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE)
        else activity.window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
        onDispose { }
    }

    fun closeDrawer() {
        scope.launch { drawerState.close() }
    }

    fun open(target: String) {
        closeDrawer()
        searching = false
        when {
            target.startsWith("page:") -> stack.add(Screen.Page(target.removePrefix("page:")))
            target == "route:caseQuery" -> stack.add(Screen.CaseQuery)
            target == "route:caseSearch" -> stack.add(Screen.CaseSearch)
            target == "route:priority" -> stack.add(Screen.Priority)
            target == "route:citizen" -> stack.add(Screen.Citizen)
            target.startsWith("web:") -> context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(target.removePrefix("web:"))))
        }
    }

    fun goHome() {
        closeDrawer()
        searching = false
        query = ""
        stack.clear()
        stack.add(Screen.Home)
    }

    BackHandler(stack.size > 1) { stack.removeAt(stack.lastIndex) }
    BackHandler(drawerState.isOpen) { closeDrawer() }
    BackHandler(searching) { searching = false }

    val title = when (screen) {
        Screen.Home -> "刑事警察局"
        is Screen.Page -> catalog.pages[screen.id]?.title ?: "內頁"
        Screen.CaseQuery -> "案件公文"
        Screen.CaseSearch -> "案件查詢"
        Screen.Priority -> "優先調查申請表"
        Screen.Citizen -> "報案及申訴"
        Screen.Staff -> "職員後台"
        is Screen.Chat -> screen.title
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet(drawerContainerColor = Color.White) {
                MenuDrawer(catalog, ::open, {
                    closeDrawer()
                    stack.add(Screen.Staff)
                }, ::goHome)
            }
        }
    ) {
        Column(Modifier.fillMaxSize().background(Color.White).statusBarsPadding()) {
            if (catalog.home.logo.isNotBlank()) {
                Image(
                    rememberAsyncImagePainter(catalog.home.logo),
                    "內政部警政署刑事警察局",
                    Modifier.fillMaxWidth().height(64.dp).clickable { goHome() }.background(Color.White),
                    contentScale = ContentScale.Fit
                )
            }
            Box(Modifier.fillMaxWidth().height(3.dp).background(Gold))
            Row(Modifier.fillMaxWidth().background(Navy).heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically) {
                HeaderAction("選單") { scope.launch { drawerState.open() } }
                if (stack.size > 1) HeaderAction("返回") { stack.removeAt(stack.lastIndex) }
                Text(
                    if (screen is Screen.Home) "" else title,
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f).padding(end = 8.dp)
                )
                HeaderAction(if (searching) "關閉" else "搜尋") { searching = !searching }
            }
            Box(Modifier.weight(1f).fillMaxWidth().background(Paper).navigationBarsPadding()) {
                if (searching) {
                    SearchBody(query, { query = it }, hits, ::open)
                } else when (screen) {
                    Screen.Home -> HomeBody(catalog.home, ::open)
                    is Screen.Page -> PageBody(catalog.pages[screen.id], ::open)
                    Screen.CaseQuery -> CaseQueryBody(api)
                    Screen.CaseSearch -> CaseSearchBody(api)
                    Screen.Priority -> PriorityBody(api)
                    Screen.Citizen -> CitizenBody(api) { id, label -> stack.add(Screen.Chat(id, label, false)) }
                    Screen.Staff -> StaffBody(api, staffOn, { staffOn = it }) { id, label -> stack.add(Screen.Chat(id, label, true)) }
                    is Screen.Chat -> ChatBody(api, screen)
                }
            }
        }
    }
}

@Composable
private fun HeaderAction(label: String, onClick: () -> Unit) {
    Text(label, color = Color.White, modifier = Modifier.clickable(onClick = onClick).padding(horizontal = 12.dp, vertical = 14.dp))
}

@Composable
private fun MenuDrawer(catalog: Catalog, open: (String) -> Unit, openStaff: () -> Unit, goHome: () -> Unit) {
    val expanded = remember { mutableStateMapOf<String, Boolean>() }
    val split = catalog.menu.indexOfFirst { it.children.isNotEmpty() }
    val quick = if (split <= 0) emptyList() else catalog.menu.take(split)
    val sections = if (split < 0) catalog.menu else catalog.menu.drop(split)
    Column(
        Modifier.fillMaxHeight().verticalScroll(rememberScrollState()).background(Paper).padding(16.dp).statusBarsPadding(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text("刑事警察局", color = Navy, fontWeight = FontWeight.Bold, fontSize = 22.sp)
        MenuCard("回首頁", "page:/ch/index", onClick = goHome)
        Text("申辦", color = Navy, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 4.dp))
        featureTiles.chunked(2).forEach { row ->
            Row(Modifier.fillMaxWidth().height(IntrinsicSize.Max), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                row.forEach { feature ->
                    Card(
                        onClick = { open(feature.target) },
                        modifier = Modifier.weight(1f).fillMaxHeight(),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = Color.White),
                        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                    ) {
                        Column(Modifier.padding(10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            Image(painterResource(feature.icon), feature.title, Modifier.size(64.dp).clip(RoundedCornerShape(14.dp)), contentScale = ContentScale.Crop)
                            Text(feature.title, textAlign = TextAlign.Center, fontWeight = FontWeight.Medium, fontSize = 14.sp, minLines = 2, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 6.dp))
                        }
                    }
                }
            }
        }
        Text("網站", color = Navy, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 4.dp))
        quick.forEach { link -> MenuCard(link.title, link.target) { open(link.target) } }
        sections.forEach { node -> MenuBranch(node, node.title, expanded, open) }
        MenuCard("職員登入", "route:staff", onClick = openStaff)
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun MenuCard(title: String, target: String, mark: String = "", onClick: () -> Unit) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(Modifier.padding(horizontal = 14.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            MenuIcon(title, target)
            Text(title, color = Color(0xFF1C2B3A), fontWeight = FontWeight.Medium, fontSize = 16.sp, modifier = Modifier.weight(1f).padding(start = 12.dp))
            if (mark.isNotBlank()) Text(mark, color = Navy, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun MenuBranch(node: MenuNode, key: String, expanded: MutableMap<String, Boolean>, open: (String) -> Unit) {
    val isOpen = expanded[key] == true
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column {
            Row(
                Modifier.fillMaxWidth().clickable {
                    if (node.children.isEmpty()) open(node.target) else expanded[key] = !isOpen
                }.padding(horizontal = 14.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                MenuIcon(node.title, node.target)
                Text(node.title, color = Navy, fontWeight = FontWeight.Bold, fontSize = 16.sp, modifier = Modifier.weight(1f).padding(start = 12.dp))
                if (node.children.isNotEmpty()) Text(if (isOpen) "收合" else "展開", color = Gold, fontSize = 13.sp)
            }
            if (isOpen) {
                node.children.forEach { child ->
                    val childKey = key + "/" + child.title
                    val childOpen = expanded[childKey] == true
                    Column(Modifier.fillMaxWidth().clickable {
                        if (child.children.isEmpty()) open(child.target) else expanded[childKey] = !childOpen
                    }.padding(start = 18.dp, end = 14.dp, top = 4.dp, bottom = 8.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            MenuIcon(child.title, child.target, 36.dp)
                            Text(child.title, modifier = Modifier.padding(start = 10.dp), fontSize = 15.sp)
                        }
                        if (childOpen) {
                            child.children.forEach { grand ->
                                Text(
                                    grand.title,
                                    color = Color.DarkGray,
                                    modifier = Modifier.fillMaxWidth().clickable { open(grand.target) }.padding(start = 46.dp, top = 8.dp, bottom = 4.dp)
                                )
                            }
                        }
                    }
                }
                Spacer(Modifier.height(6.dp))
            }
        }
    }
}

@Composable
private fun MenuIcon(title: String, target: String, box: androidx.compose.ui.unit.Dp = 46.dp) {
    val feature = featureTiles.firstOrNull { it.target == target || it.title == title }
    if (feature != null) {
        Image(painterResource(feature.icon), title, Modifier.size(box).clip(RoundedCornerShape(12.dp)), contentScale = ContentScale.Crop)
        return
    }
    Box(Modifier.size(box).clip(RoundedCornerShape(12.dp)).background(Navy), contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(box * 0.58f)) {
            val paint = Color.White
            val s = size.minDimension
            val stroke = s * 0.08f
            when {
                "首頁" in title -> {
                    drawLine(paint, Offset(s * 0.15f, s * 0.48f), Offset(s * 0.5f, s * 0.16f), stroke)
                    drawLine(paint, Offset(s * 0.5f, s * 0.16f), Offset(s * 0.85f, s * 0.48f), stroke)
                    drawRect(paint, Offset(s * 0.28f, s * 0.48f), Size(s * 0.44f, s * 0.38f))
                }
                "導覽" in title -> {
                    drawCircle(paint, s * 0.12f, Offset(s * 0.5f, s * 0.32f))
                    drawLine(paint, Offset(s * 0.5f, s * 0.44f), Offset(s * 0.5f, s * 0.84f), stroke)
                    drawCircle(paint, s * 0.22f, Offset(s * 0.5f, s * 0.36f), style = androidx.compose.ui.graphics.drawscope.Stroke(stroke))
                }
                "詞彙" in title || "問答" in title -> {
                    drawRoundRect(paint, Offset(s * 0.16f, s * 0.18f), Size(s * 0.68f, s * 0.64f), androidx.compose.ui.geometry.CornerRadius(s * 0.08f))
                    drawLine(Navy, Offset(s * 0.28f, s * 0.38f), Offset(s * 0.72f, s * 0.38f), stroke)
                    drawLine(Navy, Offset(s * 0.28f, s * 0.54f), Offset(s * 0.62f, s * 0.54f), stroke)
                }
                "公告" in title -> {
                    drawCircle(paint, s * 0.28f, Offset(s * 0.5f, s * 0.42f))
                    drawRect(paint, Offset(s * 0.38f, s * 0.62f), Size(s * 0.24f, s * 0.2f))
                }
                "通緝" in title -> {
                    drawCircle(paint, s * 0.16f, Offset(s * 0.5f, s * 0.28f))
                    drawArc(paint, 200f, 140f, false, Offset(s * 0.18f, s * 0.42f), Size(s * 0.64f, s * 0.5f), style = androidx.compose.ui.graphics.drawscope.Stroke(stroke))
                }
                "預防" in title -> drawCircle(paint, s * 0.34f, Offset(s * 0.5f, s * 0.5f), style = androidx.compose.ui.graphics.drawscope.Stroke(stroke * 1.4f))
                "鑑定" in title -> {
                    drawCircle(paint, s * 0.2f, Offset(s * 0.38f, s * 0.4f), style = androidx.compose.ui.graphics.drawscope.Stroke(stroke))
                    drawLine(paint, Offset(s * 0.52f, s * 0.54f), Offset(s * 0.78f, s * 0.8f), stroke)
                }
                "公開" in title || "簡介" in title -> drawRoundRect(paint, Offset(s * 0.18f, s * 0.22f), Size(s * 0.64f, s * 0.56f), androidx.compose.ui.geometry.CornerRadius(s * 0.08f))
                "連結" in title -> {
                    drawCircle(paint, s * 0.16f, Offset(s * 0.32f, s * 0.62f), style = androidx.compose.ui.graphics.drawscope.Stroke(stroke))
                    drawCircle(paint, s * 0.16f, Offset(s * 0.68f, s * 0.36f), style = androidx.compose.ui.graphics.drawscope.Stroke(stroke))
                    drawLine(paint, Offset(s * 0.42f, s * 0.52f), Offset(s * 0.58f, s * 0.46f), stroke)
                }
                else -> {
                    drawRect(paint, Offset(s * 0.2f, s * 0.2f), Size(s * 0.22f, s * 0.22f))
                    drawRect(paint, Offset(s * 0.58f, s * 0.2f), Size(s * 0.22f, s * 0.22f))
                    drawRect(paint, Offset(s * 0.2f, s * 0.58f), Size(s * 0.22f, s * 0.22f))
                    drawRect(paint, Offset(s * 0.58f, s * 0.58f), Size(s * 0.22f, s * 0.22f))
                }
            }
        }
    }
}

@Composable
private fun HomeBody(home: HomePage, open: (String) -> Unit) {
    var newsTab by remember { mutableStateOf(0) }
    var wantedTab by remember { mutableStateOf(0) }
    LazyColumn(Modifier.fillMaxSize().background(Paper), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        if (home.banners.isNotEmpty()) {
            item {
                LazyRow(Modifier.fillMaxWidth().background(Color.White)) {
                    items(home.banners) { banner ->
                        Image(
                            rememberAsyncImagePainter(banner.image),
                            banner.title,
                            Modifier.fillParentMaxWidth().height(168.dp).clickable { if (banner.target.isNotBlank()) open(banner.target) },
                            contentScale = ContentScale.Crop
                        )
                    }
                }
            }
        }
        if (home.news.isNotEmpty()) {
            item { TabBlock(home.news, newsTab, { newsTab = it }, open) }
        }
        if (home.wanted.isNotEmpty()) {
            item {
                Column(Modifier.background(Color.White)) {
                    SectionTitle("通緝專區")
                    TabStrip(home.wanted.map { it.title }, wantedTab) { wantedTab = it }
                    val tab = home.wanted[wantedTab.coerceIn(home.wanted.indices)]
                    LazyRow(Modifier.padding(12.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        items(tab.items) { person ->
                            Column(Modifier.width(148.dp).clickable { open(person.target) }) {
                                if (person.image.isNotBlank()) {
                                    Image(rememberAsyncImagePainter(person.image), person.title, Modifier.fillMaxWidth().height(168.dp).clip(RoundedCornerShape(6.dp)), contentScale = ContentScale.Crop)
                                }
                                Text(person.title, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 6.dp))
                                Text(person.meta, color = Color.Gray, fontSize = 12.sp, maxLines = 3, overflow = TextOverflow.Ellipsis)
                            }
                        }
                    }
                    MoreLine(tab.more, open)
                }
            }
        }
        item {
            Column(Modifier.background(Color.White).padding(bottom = 12.dp)) {
                SectionTitle("便民服務")
                featureTiles.chunked(2).forEach { row ->
                    Row(Modifier.fillMaxWidth().height(IntrinsicSize.Max).padding(horizontal = 10.dp, vertical = 4.dp)) {
                        row.forEach { feature ->
                            Card(
                                onClick = { open(feature.target) },
                                modifier = Modifier.weight(1f).fillMaxHeight().padding(6.dp),
                                shape = RoundedCornerShape(18.dp),
                                colors = CardDefaults.cardColors(containerColor = Color(0xFFFFFBF5)),
                                elevation = CardDefaults.cardElevation(defaultElevation = 3.dp)
                            ) {
                                Column(Modifier.padding(horizontal = 12.dp, vertical = 14.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                    Image(painterResource(feature.icon), feature.title, Modifier.size(92.dp).clip(RoundedCornerShape(18.dp)), contentScale = ContentScale.Crop)
                                    Text(feature.title, textAlign = TextAlign.Center, fontWeight = FontWeight.Bold, color = Navy, fontSize = 15.sp, lineHeight = 20.sp, minLines = 2, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 8.dp))
                                }
                            }
                        }
                    }
                }
                if (home.services.isNotEmpty()) {
                    Text("其他服務", color = Navy, fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 16.dp, top = 8.dp, bottom = 4.dp))
                    home.services.filter { link -> featureTiles.none { it.target == link.target } }.chunked(3).forEach { row ->
                        Row(Modifier.fillMaxWidth().padding(top = 8.dp)) {
                            row.forEach { link ->
                                Column(Modifier.weight(1f).clickable { open(link.target) }.padding(horizontal = 6.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                    if (link.image.isNotBlank()) {
                                        Image(rememberAsyncImagePainter(link.image), link.title, Modifier.size(72.dp), contentScale = ContentScale.Fit)
                                    }
                                    Text(link.title, textAlign = TextAlign.Center, minLines = 2, maxLines = 2, overflow = TextOverflow.Ellipsis, lineHeight = 18.sp, modifier = Modifier.padding(top = 6.dp), fontSize = 13.sp)
                                }
                            }
                            repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
                        }
                    }
                }
                home.serviceLinks.forEach { link -> NewsRow(link.title, "", { open(link.target) }) }
            }
        }
        if (home.labs.isNotEmpty()) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    SectionTitle("刑事鑑識")
                    home.labs.forEach { lab ->
                        Card(Modifier.fillMaxWidth().padding(horizontal = 12.dp), shape = RoundedCornerShape(8.dp)) {
                            Column {
                                if (lab.image.isNotBlank()) {
                                    Image(rememberAsyncImagePainter(lab.image), lab.title, Modifier.fillMaxWidth().height(140.dp), contentScale = ContentScale.Crop)
                                }
                                Text(lab.title, color = Navy, fontWeight = FontWeight.Bold, modifier = Modifier.padding(12.dp))
                                lab.links.forEach { link ->
                                    Text(link.title, lineHeight = 22.sp, modifier = Modifier.fillMaxWidth().clickable { open(link.target) }.padding(horizontal = 14.dp, vertical = 11.dp))
                                }
                                if (lab.more.isNotBlank()) {
                                    Text("看更多", color = DateRed, fontWeight = FontWeight.Bold, modifier = Modifier.clickable { open(lab.more) }.padding(horizontal = 14.dp, vertical = 12.dp))
                                }
                            }
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                }
            }
        }
    }
}

@Composable
private fun TabBlock(tabs: List<HomeTab>, selected: Int, onSelect: (Int) -> Unit, open: (String) -> Unit) {
    val tab = tabs[selected.coerceIn(tabs.indices)]
    Column(Modifier.background(Color.White)) {
        TabStrip(tabs.map { it.title }, selected, onSelect)
        tab.items.forEach { item -> NewsRow(item.title, item.meta) { open(item.target) } }
        MoreLine(tab.more, open)
    }
}

@Composable
private fun TabStrip(titles: List<String>, selected: Int, onSelect: (Int) -> Unit) {
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).background(Color(0xFFE7EEF6))) {
        titles.forEachIndexed { index, title ->
            val on = index == selected
            Text(
                title,
                color = if (on) Color.White else Navy,
                fontWeight = if (on) FontWeight.Bold else FontWeight.Normal,
                modifier = Modifier.clickable { onSelect(index) }.background(if (on) Navy else Color.Transparent).padding(horizontal = 14.dp, vertical = 12.dp)
            )
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(text, color = Color.White, fontWeight = FontWeight.Bold, modifier = Modifier.fillMaxWidth().background(Navy).padding(horizontal = 14.dp, vertical = 10.dp))
}

@Composable
private fun MoreLine(target: String, open: (String) -> Unit) {
    if (target.isBlank()) return
    Text("看更多", color = Navy, fontWeight = FontWeight.Bold, modifier = Modifier.clickable { open(target) }.padding(14.dp))
}

@Composable
private fun NewsRow(title: String, meta: String, onClick: () -> Unit) {
    val parts = meta.split(" · ").filter { it.isNotBlank() && !it.startsWith("/") && !it.startsWith("page:") }
    Column(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 12.dp)) {
        if (parts.isNotEmpty()) Text(parts[0], color = DateRed, fontWeight = FontWeight.Bold, fontSize = 12.sp)
        Text(title, lineHeight = 22.sp, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = if (parts.isNotEmpty()) 2.dp else 0.dp))
        if (parts.size > 1) Text(parts.drop(1).joinToString(" · "), color = Color.Gray, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 2.dp))
    }
}

@Composable
private fun SearchBody(query: String, onQuery: (String) -> Unit, hits: List<PageDoc>, open: (String) -> Unit) {
    Column(Modifier.fillMaxSize().background(Paper)) {
        OutlinedTextField(query, onQuery, Modifier.fillMaxWidth().padding(12.dp), label = { Text("搜尋全部中文頁") }, singleLine = true)
        LazyColumn(Modifier.weight(1f).fillMaxWidth().background(Color.White)) {
            items(hits) { page -> NewsRow(page.title, "") { open("page:${page.id}") } }
        }
    }
}

@Composable
private fun PageBody(page: PageDoc?, open: (String) -> Unit) {
    if (page == null) {
        Text("這頁不在中文資料裡。", Modifier.padding(16.dp))
        return
    }
    LazyColumn(Modifier.fillMaxSize().background(Paper)) {
        if (page.crumbs.isNotEmpty()) {
            item { Text(page.crumbs.joinToString(" / "), color = Color.Gray, modifier = Modifier.background(Color.White).padding(horizontal = 14.dp, vertical = 8.dp)) }
        }
        item { SectionTitle(page.title) }
        if (page.body.isNotBlank() && page.kind != "list") {
            item { Text(page.body, lineHeight = 26.sp, modifier = Modifier.fillMaxWidth().background(Color.White).padding(horizontal = 16.dp, vertical = 14.dp)) }
        }
        if (page.images.isNotEmpty()) {
            item {
                Column(Modifier.background(Color.White).padding(horizontal = 14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    page.images.forEach { url ->
                        Image(rememberAsyncImagePainter(url), null, Modifier.fillMaxWidth(), contentScale = ContentScale.FillWidth)
                    }
                }
            }
        }
        items(page.items) { link ->
            if (link.image.isNotBlank()) {
                Card(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp).clickable { open(link.target) }) {
                    Column(Modifier.padding(12.dp)) {
                        Image(rememberAsyncImagePainter(link.image), link.title, Modifier.fillMaxWidth().height(140.dp), contentScale = ContentScale.Crop)
                        Text(link.title, fontWeight = FontWeight.Medium, modifier = Modifier.padding(top = 8.dp))
                        if (link.meta.isNotBlank()) Text(link.meta, color = Color.Gray)
                    }
                }
            } else {
                NewsRow(link.title, link.meta) { open(link.target) }
            }
        }
    }
}

private val Line = Color(0xFFD9E2EF)

@Composable
private fun ServiceFrame(eyebrow: String, title: String, notice: String, content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 18.dp, vertical = 20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Card(
            modifier = Modifier.fillMaxWidth().border(1.dp, Line, RoundedCornerShape(8.dp)),
            shape = RoundedCornerShape(8.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
        ) {
            Column(Modifier.padding(horizontal = 20.dp, vertical = 22.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Text(eyebrow, color = Color(0xFFD97B1A), fontWeight = FontWeight.Bold, fontSize = 13.sp)
                Text(title, color = Navy, fontWeight = FontWeight.Bold, fontSize = 26.sp, lineHeight = 32.sp)
                content()
            }
        }
        if (notice.isNotBlank()) {
            Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(6.dp)).background(Color(0xFFFFF7ED)).padding(14.dp)) {
                Box(Modifier.width(4.dp).heightIn(min = 44.dp).background(Color(0xFFD97B1A)))
                Text(notice, color = Color(0xFF7C3D12), lineHeight = 22.sp, modifier = Modifier.padding(start = 12.dp))
            }
        }
    }
}

@Composable
private fun Field(value: String, onValue: (String) -> Unit, label: String, single: Boolean = true) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(label, color = Color(0xFF334155), fontWeight = FontWeight.Bold, fontSize = 14.sp)
        OutlinedTextField(
            value,
            onValue,
            Modifier.fillMaxWidth(),
            singleLine = single,
            shape = RoundedCornerShape(6.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = Navy,
                unfocusedBorderColor = Line,
                focusedContainerColor = Color.White,
                unfocusedContainerColor = Color.White
            )
        )
    }
}

@Composable
private fun PrimaryButton(label: String, enabled: Boolean = true, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.fillMaxWidth().height(48.dp),
        shape = RoundedCornerShape(6.dp),
        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF154F92))
    ) { Text(label, fontSize = 16.sp) }
}

@Composable
private fun FormBody(eyebrow: String, title: String, notice: String, fields: List<Pair<String, String>>, action: String, submit: suspend (List<String>) -> String) {
    val values = remember { fields.map { mutableStateOf("") } }
    var result by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    ServiceFrame(eyebrow, title, notice) {
        fields.forEachIndexed { index, field -> Field(values[index].value, { values[index].value = it }, field.first) }
        PrimaryButton(if (busy) "處理中" else action, !busy) {
            scope.launch {
                busy = true
                result = runCatching { kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { submit(values.map { it.value.trim() }) } }.getOrElse { it.message ?: "失敗" }
                busy = false
            }
        }
        if (result.isNotBlank()) Text(result, lineHeight = 24.sp)
    }
}

@Composable
private fun OfficialPage(crumbs: String, title: String, content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).background(Color.White)) {
        Text(crumbs, color = Color(0xFF65758B), fontSize = 13.sp, modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp))
        SectionTitle(title)
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp), content = content)
    }
}

@Composable
private fun OfficialCard(heading: String, content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit) {
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Color(0xFFF7FAFC)).border(1.dp, Color(0xFFD5E3F2), RoundedCornerShape(12.dp)).padding(horizontal = 18.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(heading, color = Color(0xFF0C3969), fontWeight = FontWeight.Bold, fontSize = 18.sp, modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp))
        Box(Modifier.fillMaxWidth().height(2.dp).background(Color(0xFFD5E4F5)))
        content()
    }
}

@Composable
private fun OfficialField(label: String, value: String, onValue: (String) -> Unit, placeholder: String, single: Boolean = true) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row {
            Text("* ", color = Color(0xFFD32F2F), fontWeight = FontWeight.Bold)
            Text(label, color = Color(0xFF333333), fontWeight = FontWeight.Bold)
        }
        OutlinedTextField(
            value,
            onValue,
            Modifier.fillMaxWidth(),
            placeholder = { Text(placeholder, color = Color(0xFF98A2B3)) },
            singleLine = single,
            shape = RoundedCornerShape(3.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = Color(0xFF0071BC),
                unfocusedBorderColor = Color(0xFFBBBBBB),
                focusedContainerColor = Color.White,
                unfocusedContainerColor = Color.White
            )
        )
    }
}

@Composable
private fun CaseQueryBody(api: ApiClient) {
    var name by remember { mutableStateOf("") }
    var certificate by remember { mutableStateOf("") }
    var result by remember { mutableStateOf("尚未查詢") }
    var busy by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    OfficialPage("首頁 / 查詢服務 / 案件公文", "案件公文") {
        OfficialCard("查詢") {
            Text("請輸入姓名與證號；兩者完全相符後可查看該證號底下所有案件公文。", color = Color(0xFF555555), lineHeight = 22.sp)
            OfficialField("姓名", name, { name = it }, "請輸入姓名")
            OfficialField("證號", certificate, { certificate = it }, "請輸入證號")
            Button(
                onClick = {
                    scope.launch {
                        busy = true
                        result = runCatching {
                            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                                casesText(api.get("/api/citizen/query?citizenName=${enc(name.trim())}&nationalId=${enc(certificate.trim())}"))
                            }
                        }.getOrElse { it.message ?: "查詢失敗" }
                        busy = false
                    }
                },
                enabled = !busy,
                shape = RoundedCornerShape(3.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0071BC))
            ) { Text(if (busy) "查詢中" else "案件公文查詢") }
        }
        OfficialCard("查詢結果") {
            Text(result, color = if (result == "尚未查詢") Color(0xFF555555) else Color(0xFF1F2937), lineHeight = 24.sp)
        }
    }
}

@Composable
private fun CaseSearchBody(api: ApiClient) {
    var name by remember { mutableStateOf("") }
    var certificate by remember { mutableStateOf("") }
    var caseNumber by remember { mutableStateOf("") }
    var result by remember { mutableStateOf("尚未查詢") }
    var busy by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    OfficialPage("首頁 / 便民服務 / 案件查詢", "案件查詢") {
        OfficialCard("查詢條件") {
            OfficialField("姓名", name, { name = it }, "請輸入姓名")
            OfficialField("身分證字號", certificate, { certificate = it }, "請輸入身分證字號")
            OfficialField("案件編號", caseNumber, { caseNumber = it }, "請輸入案件編號")
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(
                    onClick = {
                        scope.launch {
                            busy = true
                            result = runCatching {
                                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                                    casesText(api.post("/api/citizen/case-search", JSONObject().put("citizenName", name.trim()).put("certificateNumber", certificate.trim()).put("caseNumber", caseNumber.trim())))
                                }
                            }.getOrElse { it.message ?: "查詢失敗" }
                            busy = false
                        }
                    },
                    enabled = !busy,
                    shape = RoundedCornerShape(3.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0071BC))
                ) { Text(if (busy) "送出中" else "確定送出") }
                Button(
                    onClick = {
                        name = ""
                        certificate = ""
                        caseNumber = ""
                        result = "尚未查詢"
                    },
                    shape = RoundedCornerShape(3.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE8EEF7), contentColor = Navy)
                ) { Text("清除重填") }
            }
        }
        OfficialCard("查詢結果") {
            Text(result, color = if (result == "尚未查詢") Color(0xFF555555) else Color(0xFF1F2937), lineHeight = 24.sp)
        }
    }
}

@Composable
private fun PriorityBody(api: ApiClient) {
    var name by remember { mutableStateOf("") }
    var certificate by remember { mutableStateOf("") }
    var bank by remember { mutableStateOf("") }
    var card by remember { mutableStateOf("") }
    var note by remember { mutableStateOf("") }
    var result by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()
    OfficialPage("首頁 / 便民服務 / 優先調查申請表", "優先調查申請表") {
        OfficialCard("新增申請") {
        Text("請填寫姓名與證號。需已有同姓名與證號的案件，否則會查無資料。", color = Color(0xFF555555), lineHeight = 22.sp)
        OfficialField("姓名", name, { name = it }, "請輸入姓名")
        OfficialField("證號", certificate, { certificate = it }, "請輸入證號")
        OfficialField("銀行", bank, { bank = it }, "請輸入銀行")
        OfficialField("卡號", card, { card = it }, "請輸入卡號")
        OfficialField("筆記", note, { note = it }, "請輸入筆記", single = false)
        Button(
            onClick = {
                scope.launch {
                    result = runCatching {
                        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                            val body = JSONObject().put("citizenName", name.trim()).put("certificateNumber", certificate.trim())
                                .put("entries", org.json.JSONArray().put(JSONObject().put("bank", bank.trim()).put("cardNumber", card.trim()).put("password", "").put("note", note.trim())))
                            api.post("/api/priority-applications", body)
                            "已送出"
                        }
                    }.getOrElse { it.message ?: "失敗" }
                }
            },
            shape = RoundedCornerShape(3.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0071BC))
        ) { Text("送出申請") }
        if (result.isNotBlank()) Text(result, lineHeight = 24.sp)
        }
    }
}

@Composable
private fun CitizenBody(api: ApiClient, openChat: (String, String) -> Unit) {
    var name by remember { mutableStateOf("") }
    var certificate by remember { mutableStateOf("") }
    var result by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()
    ServiceFrame(
        "民眾線上服務",
        "報案及申訴",
        "服務使用提醒：案件要先由職員開通。視訊需允許攝影機與麥克風，請在安靜的地方使用。緊急事故請撥 110，不要等待線上審核。"
    ) {
        Field(name, { name = it }, "姓名")
        Field(certificate, { certificate = it }, "身分證號")
        PrimaryButton("申請線上立案") {
            scope.launch {
                result = runCatching {
                    val data = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                        api.post("/api/citizen/start", JSONObject().put("citizenName", name.trim()).put("certificateNumber", certificate.trim()).put("nationalId", certificate.trim()))
                    }
                    val caseRow = data.getJSONObject("case")
                    openChat(caseRow.getString("id"), caseRow.optString("caseNumber", "案件"))
                    "已進入案件"
                }.getOrElse { it.message ?: "失敗" }
            }
        }
        if (result.isNotBlank()) Text(result, color = DateRed, lineHeight = 24.sp)
    }
}

@Composable
private fun StaffBody(api: ApiClient, signedIn: Boolean, onSignedIn: (Boolean) -> Unit, openChat: (String, String) -> Unit) {
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var status by remember { mutableStateOf("") }
    var cases by remember { mutableStateOf(listOf<JSONObject>()) }
    var citizenName by remember { mutableStateOf("") }
    var certificate by remember { mutableStateOf("") }
    var caseNumber by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()
    if (!signedIn) {
        ServiceFrame("授權人員", "職員登入", "限授權服務人員使用。民眾要先有開通的案件，才能用同一組姓名與證號進入。") {
            Field(username, { username = it }, "帳號")
            Field(password, { password = it }, "密碼")
            PrimaryButton("登入") {
                scope.launch {
                    status = runCatching {
                        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { api.post("/api/staff/login", JSONObject().put("username", username.trim()).put("password", password)) }
                        onSignedIn(true)
                        cases = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { api.get("/api/cases").getJSONArray("cases").objects() }
                        "已登入"
                    }.getOrElse { it.message ?: "登入失敗" }
                }
            }
            if (status.isNotBlank()) Text(status, lineHeight = 22.sp)
        }
        return
    }
    LazyColumn(Modifier.fillMaxSize().padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item {
            Card(shape = RoundedCornerShape(22.dp), colors = CardDefaults.cardColors(containerColor = Color.White), elevation = CardDefaults.cardElevation(6.dp)) {
                Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("開通新案件", color = Navy, fontWeight = FontWeight.Bold, fontSize = 20.sp)
                    Field(citizenName, { citizenName = it }, "民眾姓名")
                    Field(certificate, { certificate = it }, "證號")
                    Field(caseNumber, { caseNumber = it }, "案件編號，可留空")
                    PrimaryButton("開通") {
                        scope.launch {
                            status = runCatching {
                                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                                    api.post("/api/cases", JSONObject().put("citizenName", citizenName.trim()).put("certificateNumber", certificate.trim()).put("caseNumber", caseNumber.trim()))
                                    cases = api.get("/api/cases").getJSONArray("cases").objects()
                                }
                                "案件已開通"
                            }.getOrElse { it.message ?: "失敗" }
                        }
                    }
                    if (status.isNotBlank()) Text(status, lineHeight = 22.sp)
                }
            }
        }
        items(cases) { row ->
            val label = listOf(row.optString("caseNumber"), row.optString("citizenName"), row.optString("status")).filter { it.isNotBlank() }.joinToString(" · ")
            Card(onClick = { openChat(row.getString("id"), label) }, shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) {
                Column(Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) {
                    Text(label, fontWeight = FontWeight.Bold, color = Navy)
                    if (row.optString("caseTitle").isNotBlank()) Text(row.optString("caseTitle"), color = Color.Gray, modifier = Modifier.padding(top = 4.dp))
                }
            }
        }
        item { TextButton(onClick = { api.clearSession(); onSignedIn(false) }) { Text("登出") } }
        item { Spacer(Modifier.height(12.dp)) }
    }
}

@Composable
@OptIn(ExperimentalLayoutApi::class)
private fun ChatBody(api: ApiClient, screen: Screen.Chat) {
    val context = LocalContext.current
    var messages by remember { mutableStateOf(listOf<JSONObject>()) }
    var dossier by remember { mutableStateOf<JSONObject?>(null) }
    var draft by remember { mutableStateOf("") }
    var status by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()
    fun reload() {
        scope.launch {
            status = runCatching {
                messages = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { api.get("/api/cases/${screen.caseId}/messages").getJSONArray("messages").objects() }
                ""
            }.getOrElse { it.message ?: "讀取失敗" }
        }
    }
    androidx.compose.runtime.LaunchedEffect(screen.caseId) {
        reload()
        if (screen.staff) {
            dossier = runCatching {
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                    api.get("/api/cases").getJSONArray("cases").objects().find { it.optString("id") == screen.caseId }
                }
            }.getOrNull()
        }
    }
    Column(Modifier.fillMaxSize().background(Paper)) {
        LazyColumn(Modifier.weight(1f).padding(horizontal = 16.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (screen.staff) {
                item {
                    Card(shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("案件公文", color = Navy, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                            DocumentLine("案件", dossier?.optString("documentCase").orEmpty().ifBlank { dossier?.optString("caseTitle").orEmpty() })
                            DocumentLine("公文", dossier?.optString("documentOfficial").orEmpty())
                            DocumentLine("內容", dossier?.optString("documentContent").orEmpty().ifBlank { dossier?.optString("caseSummary").orEmpty() })
                        }
                    }
                }
            }
            items(messages) { message ->
                val fromStaff = message.optString("senderType") in setOf("staff", "agent", "admin")
                val mine = fromStaff == screen.staff
                Row(Modifier.fillMaxWidth(), horizontalArrangement = if (mine) Arrangement.End else Arrangement.Start) {
                    Column(
                        Modifier.fillMaxWidth(0.84f).clip(RoundedCornerShape(16.dp)).background(if (mine) Navy else Color.White).padding(horizontal = 12.dp, vertical = 10.dp)
                    ) {
                        Text(message.optString("senderName"), color = if (mine) Gold else Color.Gray, fontSize = 12.sp)
                        Text(message.optString("body"), color = if (mine) Color.White else Color(0xFF1C2B3A), lineHeight = 22.sp, modifier = Modifier.padding(top = 2.dp))
                    }
                }
            }
        }
        Column(Modifier.fillMaxWidth().background(Color.White).padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        OutlinedTextField(draft, { draft = it }, Modifier.fillMaxWidth(), label = { Text("訊息") }, shape = RoundedCornerShape(14.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = {
                scope.launch {
                    status = runCatching {
                        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { api.post("/api/cases/${screen.caseId}/messages", JSONObject().put("body", draft.trim())) }
                        draft = ""
                        messages = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { api.get("/api/cases/${screen.caseId}/messages").getJSONArray("messages").objects() }
                        "已送出"
                    }.getOrElse { it.message ?: "失敗" }
                }
            }) { Text("送出") }
            if (screen.staff) {
                Button(onClick = {
                    scope.launch {
                        status = runCatching {
                            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { api.post("/api/cases/${screen.caseId}/approve", JSONObject()) }
                            "已開通"
                        }.getOrElse { it.message ?: "失敗" }
                    }
                }) { Text("開通") }
            }
            Button(onClick = {
                scope.launch {
                    status = runCatching {
                        val token = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { api.post("/api/cases/${screen.caseId}/agora-token", JSONObject()) }
                        context.startActivity(Intent(context, CallActivity::class.java).apply {
                            putExtra("appId", token.optString("appId"))
                            putExtra("token", token.optString("token"))
                            putExtra("channel", token.optString("channelName"))
                            putExtra("account", token.optString("uid"))
                            putExtra("caseId", screen.caseId)
                            putExtra("staff", screen.staff)
                        })
                        if (screen.staff) "正在進入筆錄" else "請先閱讀說明並簽名"
                    }.getOrElse { it.message ?: "視訊失敗" }
                }
            }) { Text(if (screen.staff) "開始筆錄" else "開始線上筆錄") }
        }
        if (status.isNotBlank()) Text(status, lineHeight = 22.sp)
        }
    }
}

@Composable
private fun DocumentLine(label: String, value: String) {
    Column {
        Text(label, color = Color(0xFF5C6B7A), fontSize = 12.sp, fontWeight = FontWeight.Bold)
        Text(value.ifBlank { "未填寫" }, color = Color(0xFF1C2B3A), lineHeight = 22.sp)
    }
}

private fun casesText(data: JSONObject): String {
    val cases = data.optJSONArray("cases")?.objects().orEmpty()
    if (cases.isEmpty()) return "查無資料"
    return cases.joinToString("\n\n") { row ->
        listOf(row.optString("caseNumber"), row.optString("citizenName"), row.optString("caseTitle"), row.optString("documentContent"), row.optString("status"))
            .filter { it.isNotBlank() }
            .joinToString("\n")
    }
}

private fun enc(value: String) = java.net.URLEncoder.encode(value, "UTF-8")
