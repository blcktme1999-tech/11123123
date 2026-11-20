import fs from 'fs';
import path from 'path';
import { fileURLToPath } from 'url';

const here = path.dirname(fileURLToPath(import.meta.url));
const root = path.resolve(here, '..', '..', 'official-base');
const chDir = path.join(root, 'ch');
const middleware = fs.readFileSync(path.join(root, 'middleware.js'), 'utf8');
const mapStart = middleware.indexOf('{');
const mapEnd = middleware.indexOf('};');
const pageMap = JSON.parse(middleware.slice(mapStart, mapEnd + 1));

function decode(value) {
  return String(value || '')
    .replace(/&#(\d+);/g, (_, code) => String.fromCodePoint(Number(code)))
    .replace(/&#x([0-9a-f]+);/gi, (_, code) => String.fromCodePoint(parseInt(code, 16)))
    .replace(/&#39;|&apos;/g, "'")
    .replace(/&quot;/g, '"')
    .replace(/&nbsp;/g, ' ')
    .replace(/&lt;/g, '<')
    .replace(/&gt;/g, '>')
    .replace(/&amp;/g, '&');
}

function absUrl(src) {
  const clean = decode(src).trim();
  if (!clean) return '';
  if (/^https?:\/\//i.test(clean)) return clean;
  return 'https://www.cib.npa.gov.tw' + (clean.startsWith('/') ? clean : '/' + clean);
}

function textOf(html) {
  return decode(String(html || '')
    .replace(/<script[\s\S]*?<\/script>/gi, '')
    .replace(/<style[\s\S]*?<\/style>/gi, '')
    .replace(/<br\s*\/?>/gi, '\n')
    .replace(/<\/p>/gi, '\n')
    .replace(/<[^>]+>/g, ' ')
    .replace(/[ \t]+\n/g, '\n')
    .replace(/[ \t]{2,}/g, ' ')
    .replace(/\n{3,}/g, '\n\n'))
    .trim();
}

function attr(source, name) {
  const match = source.match(new RegExp(name + '\\s*=\\s*(?:"([^"]*)"|\'([^\']*)\')', 'i'));
  return decode(match ? (match[1] || match[2] || '') : '');
}

function walk(dir, out = []) {
  for (const name of fs.readdirSync(dir)) {
    const full = path.join(dir, name);
    if (fs.statSync(full).isDirectory()) walk(full, out);
    else if (name.endsWith('.html')) out.push(full);
  }
  return out;
}

const ids = new Set();
for (const file of walk(chDir)) {
  const rel = path.relative(chDir, file).split(path.sep).join('/');
  ids.add('/ch/' + rel.replace(/\.html$/, ''));
}

function sortedQuery(search) {
  const params = new URLSearchParams(search);
  return [...params.entries()].sort((a, b) => a[0] === b[0] ? (a[1] < b[1] ? -1 : a[1] > b[1] ? 1 : 0) : (a[0] < b[0] ? -1 : 1))
    .map(([key, value]) => key + '=' + value).join('&');
}

function targetOf(href) {
  if (!href || href.startsWith('javascript:') || href === '#') return '';
  const clean = decode(href).trim();
  if (clean.startsWith('/public-services/case-query')) return 'route:caseQuery';
  if (clean.startsWith('/public-services/case-search')) return 'route:caseSearch';
  if (clean.startsWith('/public-services/priority-investigation')) return 'route:priority';
  if (clean === '/service' || clean.startsWith('/service?') || clean.startsWith('/service/')) return 'route:citizen';
  if (/^https?:\/\//i.test(clean)) {
    if (clean.startsWith('https://www.cib.npa.gov.tw/ch/') || clean.startsWith('https://www.cib.npa.gov.tw/en/')) {
      return targetOf(clean.replace('https://www.cib.npa.gov.tw', ''));
    }
    return 'web:' + clean;
  }
  if (clean.startsWith('/en/')) return 'web:https://www.cib.npa.gov.tw' + clean;
  const hashless = clean.split('#')[0];
  const q = hashless.indexOf('?');
  const pathname = (q >= 0 ? hashless.slice(0, q) : hashless).replace(/\.html$/, '');
  const query = q >= 0 ? hashless.slice(q + 1) : '';
  if (query) {
    const key = pathname + '?' + sortedQuery(query);
    if (pageMap[key] && ids.has(pageMap[key])) return 'page:' + pageMap[key];
  }
  if (ids.has(pathname)) return 'page:' + pathname;
  if (pathname === '/ch' || pathname === '/ch/') return 'page:/ch/index';
  if (clean.startsWith('/static/') || clean.startsWith('/userfiles/') || clean.startsWith('/menuDoc/') || /\/image$|\/doc$|\/openData\//.test(pathname)) {
    return 'web:https://www.cib.npa.gov.tw' + hashless;
  }
  if (clean.startsWith('/')) return 'web:https://www.cib.npa.gov.tw' + hashless;
  return '';
}

function anchors(block) {
  const items = [];
  const re = /<a\b([^>]*)>([\s\S]*?)<\/a>/gi;
  let match;
  while ((match = re.exec(block))) {
    const href = attr(match[1], 'href');
    const inner = match[2];
    const subject = (inner.match(/<p class="subject">([\s\S]*?)<\/p>/i) || [])[1];
    const title = textOf(subject || attr(match[1], 'title') || inner).replace(/^\d+\./, '');
    const target = targetOf(href);
    if (!title || !target || title === ':::') continue;
    const meta = [...inner.matchAll(/<p class="color0[12]">([\s\S]*?)<\/p>/gi)].map((item) => textOf(item[1])).filter(Boolean).join(' · ');
    const image = (inner.match(/<img\b[^>]*src="([^"]+)"/i) || [])[1] || '';
    items.push({ title: title.slice(0, 240), meta: meta.slice(0, 240), image: image ? 'https://www.cib.npa.gov.tw' + decode(image) : '', target });
  }
  return items;
}

function sliceBetween(html, startMark, endMark) {
  const start = html.indexOf(startMark);
  if (start < 0) return '';
  const end = html.indexOf(endMark, start);
  return html.slice(start, end > start ? end : Math.min(html.length, start + 250000));
}

function parseMenu(html) {
  const block = sliceBetween(html, 'class="menu"', 'id="fs"');
  const tokens = block.match(/<\/?ul\b[^>]*>|<\/?li\b[^>]*>|<a\b[^>]*>[\s\S]*?<\/a>/gi) || [];
  const stack = [{ children: [] }];
  for (const token of tokens) {
    if (/^<ul/i.test(token)) {
      const parent = stack[stack.length - 1];
      const node = parent.children[parent.children.length - 1];
      if (!node) continue;
      node.children = node.children || [];
      stack.push(node);
    } else if (/^<\/ul/i.test(token)) {
      if (stack.length > 1) stack.pop();
    } else if (/^<a/i.test(token)) {
      const href = attr(token, 'href');
      const title = textOf(token.replace(/^<a\b[^>]*>/i, '').replace(/<\/a>$/i, ''));
      const target = targetOf(href);
      if (!title || title === ':::' || title === 'English') continue;
      stack[stack.length - 1].children.push({ title: title.slice(0, 80), target, children: [] });
    }
  }
  return stack[0].children.filter((item) => item.title && item.title !== '回首頁');
}

function pageDoc(file) {
  const html = fs.readFileSync(file, 'utf8');
  const rel = '/ch/' + path.relative(chDir, file).split(path.sep).join('/').replace(/\.html$/, '');
  const main = sliceBetween(html, 'id="fs"', 'class="fatfoot');
  const title = textOf((main.match(/<h2 class="p_title">[\s\S]*?<p>([\s\S]*?)<\/p>/i) || [])[1]
    || (html.match(/<title>([\s\S]*?)<\/title>/i) || [])[1]
    || rel);
  const crumbs = [...main.matchAll(/<li class="breadcrumb-item[^"]*"[^>]*>([\s\S]*?)<\/li>/gi)].map((item) => textOf(item[1])).filter(Boolean);
  const listBlock = (main.match(/<div class="list">([\s\S]*?)<\/ul>/i) || [])[1] || '';
  const menuBlock = (main.match(/<div class="page_menu">([\s\S]*?)<\/ul>/i) || [])[1] || '';
  const iconBlock = (html.match(/<div class="iconlist">([\s\S]*?)<\/div>\s*<\/div>/i) || [])[1] || '';
  let items = anchors(listBlock);
  let kind = 'list';
  if (!items.length) {
    items = anchors(menuBlock);
    kind = items.length ? 'folder' : 'page';
  }
  if (rel === '/ch/index') {
    items = anchors(iconBlock).concat(items);
    kind = 'home';
  }
  let body = '';
  let images = [];
  if (kind === 'page' || kind === 'home') {
    const editorStart = main.indexOf('class="editor"');
    const editor = editorStart >= 0 ? main.slice(editorStart, editorStart + 80000) : '';
    const source = editor || main;
    body = textOf(source).slice(0, 12000);
    images = [...source.matchAll(/<img\b[^>]*src="([^"]+)"/gi)]
      .map((item) => decode(item[1]))
      .filter((src) => src && !src.includes('icon_'))
      .slice(0, 8)
      .map((src) => src.startsWith('http') ? src : 'https://www.cib.npa.gov.tw' + src);
    if (items.length && kind !== 'home') kind = 'folder';
    if (!items.length && body.length > 80) kind = 'article';
  }
  const articleTitle = textOf((main.match(/<div class="d_title">([\s\S]*?)<\/div>/i) || [])[1]);
  return {
    id: rel,
    title: (articleTitle || title || '內頁').replace(/\s*-\s*內政部警政署刑事警察局全球資訊網\s*$/, '').slice(0, 160),
    kind,
    crumbs,
    items: items.slice(0, 200),
    body,
    images
  };
}

function tabNames(html) {
  return [...html.matchAll(/<ul class="nav nav-tabs[\s\S]*?<\/ul>/gi)].map((match) =>
    [...match[0].matchAll(/>([^<]+)<\/a>/g)].map((item) => textOf(item[1])).filter(Boolean)
  );
}

function panesOf(html, contentId, untilMark) {
  const start = html.indexOf(`id="${contentId}"`);
  if (start < 0) return [];
  const end = untilMark ? html.indexOf(untilMark, start) : -1;
  const region = html.slice(start, end > start ? end : start + 180000);
  return region.split(/<div class="tab-pane\b/i).slice(1);
}

function moreTarget(block) {
  const tag = (block.match(/<div class="more">[\s\S]*?<a\b([^>]*)>/i) || [])[1] || '';
  return targetOf(attr(tag, 'href'));
}

function wantedItems(block) {
  return block.split(/<div class="tabpic">/i).slice(1).map((card) => {
    const tag = (card.match(/<a\b([^>]*)>/i) || [])[1] || '';
    const name = textOf((card.match(/<p class="text02">([\s\S]*?)<\/p>/i) || [])[1]);
    const code = textOf((card.match(/<p class="text04">([\s\S]*?)<\/p>/i) || [])[1]);
    const unit = textOf((card.match(/<p class="text01">([\s\S]*?)<\/p>/i) || [])[1]);
    const date = textOf((card.match(/<p class="text03">([\s\S]*?)<\/p>/i) || [])[1]);
    const image = (card.match(/<img\b[^>]*src="([^"]+)"/i) || [])[1] || '';
    return {
      title: (name || code || '通緝資料').slice(0, 80),
      meta: [code, unit, date].filter(Boolean).join(' · ').slice(0, 240),
      image: absUrl(image),
      target: targetOf(attr(tag, 'href'))
    };
  }).filter((item) => item.target).slice(0, 20);
}

function homeOf(html) {
  const bannerBlock = (html.match(/<div class="banner">([\s\S]*?)<\/div>\s*<div id="fs"/i) || [])[1] || '';
  const banners = [...bannerBlock.matchAll(/<img\b[^>]*src="([^"]+)"[^>]*>/gi)].map((match, index) => {
    const before = bannerBlock.slice(0, match.index);
    const anchor = before.lastIndexOf('<a ');
    const close = before.lastIndexOf('</a>');
    const tag = anchor > close ? (bannerBlock.slice(anchor).match(/<a\b([^>]*)>/i) || [])[1] || '' : '';
    return {
      title: textOf(attr(match[0], 'alt') || attr(tag, 'title') || '橫幅'),
      meta: '',
      image: absUrl(match[1]),
      target: targetOf(attr(tag, 'href'))
    };
  }).filter((item) => item.image);

  const names = tabNames(html);
  const newsPanes = panesOf(html, 'myTabContent', 'class="album"');
  const news = (names[0] || []).map((title, index) => ({
    title,
    more: moreTarget(newsPanes[index] || ''),
    items: anchors(newsPanes[index] || '').slice(0, 8)
  }));

  const wantedPanes = panesOf(html, 'myTab02Content', 'id="a3"');
  const wanted = (names[1] || []).map((title, index) => ({
    title,
    more: moreTarget(wantedPanes[index] || ''),
    items: wantedItems(wantedPanes[index] || '')
  }));

  const iconBlock = (html.match(/<div class="iconlist">([\s\S]*?)<\/div>\s*<div class="other">/i) || [])[1] || '';
  const otherBlock = (html.match(/<div class="other">([\s\S]*?)<\/div>/i) || [])[1] || '';
  const labHtml = sliceBetween(html, '<p>刑事鑑識</p>', 'class="fatfoot');
  const labs = labHtml.split(/<div class="box_shadow">/i).slice(1).map((box) => {
    const head = box.split(/<div class="more">/i)[0];
    const links = anchors(head);
    return {
      title: textOf((box.match(/<p class="stitle">([\s\S]*?)<\/p>/i) || [])[1]),
      image: absUrl((box.match(/<img\b[^>]*src="([^"]+)"/i) || [])[1] || ''),
      more: moreTarget(box),
      links: links.filter((link) => !link.title.includes('看更多') && !link.title.includes('MORE')).slice(0, 6)
    };
  }).filter((lab) => lab.title);

  return {
    logo: 'https://www.cib.npa.gov.tw/ch/index/viewtopimage?module=websitetop',
    banners,
    news,
    wanted,
    services: anchors(iconBlock),
    serviceLinks: anchors(otherBlock),
    labs
  };
}

const indexHtml = fs.readFileSync(path.join(chDir, 'index.html'), 'utf8');
const pages = walk(chDir).map(pageDoc);
const home = homeOf(indexHtml);
const catalog = { menu: parseMenu(indexHtml), home, pages };
const catalogText = JSON.stringify(catalog);
const outDir = path.resolve(here, '..', 'app', 'src', 'main', 'assets');
fs.mkdirSync(outDir, { recursive: true });
fs.writeFileSync(path.join(outDir, 'catalog.json'), catalogText);
fs.writeFileSync(path.join(root, 'app-catalog.json'), catalogText);
const bytes = fs.statSync(path.join(outDir, 'catalog.json')).size;
console.log(JSON.stringify({
  pages: pages.length,
  menu: catalog.menu.length,
  bytes,
  banners: home.banners.length,
  news: home.news.map((tab) => tab.title + ':' + tab.items.length),
  wanted: home.wanted.map((tab) => tab.title + ':' + tab.items.length),
  services: home.services.map((item) => item.title),
  labs: home.labs.map((lab) => lab.title)
}));
