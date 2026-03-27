import feedparser
import aiohttp
import logging
import datetime
from typing import List, Dict, Any

logger = logging.getLogger(__name__)

class NewsService:
    def __init__(self):
        self.sources = [
            "https://timesofindia.indiatimes.com/rssfeedstopstories.cms", # Times of India
            "http://feeds.bbci.co.uk/news/rss.xml", # BBC News
            "https://news.google.com/rss?hl=en-US&gl=US&ceid=US:en" # Google News
        ]
        self.cache = []
        self.browser = None # Injected via main.py

    async def deep_research(self, topic: str) -> str:
        """Uses BrowserService for a full-scale automated research task."""
        if self.browser:
            return await self.browser.search_and_summarize(topic)
        
        # Fallback to Wikipedia if browser is unavailable
        wiki_data = await self.search_wikipedia(topic)
        return wiki_data.get("summary", "Topic not found.")

    async def search_wikipedia(self, query: str) -> Dict[str, Any]:
        """Searches Wikipedia for specific topics."""
        url = "https://en.wikipedia.org/w/api.php"
        params = {
            "action": "query",
            "list": "search",
            "srsearch": query,
            "format": "json"
        }
        try:
            async with aiohttp.ClientSession() as session:
                async with session.get(url, params=params) as response:
                    data = await response.json()
                    search_results = data.get("query", {}).get("search", [])
                    if search_results:
                        top_result = search_results[0]
                        # Fetch the page content summary
                        summary_params = {
                            "action": "query",
                            "prop": "extracts",
                            "exintro": True,
                            "explaintext": True,
                            "titles": top_result["title"],
                            "format": "json"
                        }
                        async with session.get(url, params=summary_params) as summary_response:
                            summary_data = await summary_response.json()
                            pages = summary_data.get("query", {}).get("pages", {})
                            page = next(iter(pages.values()), {})
                            return {
                                "title": top_result["title"],
                                "summary": page.get("extract", "No summary found."),
                                "link": f"https://en.wikipedia.org/wiki/{top_result['title'].replace(' ', '_')}"
                            }
            return {"status": "error", "message": "Topic not found on Wikipedia."}
        except Exception as e:
            logger.error(f"Wikipedia search failed for '{query}': {e}")
            return {"status": "error", "message": str(e)}

    async def fetch_feeds(self) -> List[Dict[str, Any]]:
        """Fetches and parses RSS feeds from configured sources."""
        items = []
        async with aiohttp.ClientSession() as session:
            for url in self.sources:
                try:
                    async with session.get(url, timeout=5) as response:
                        text = await response.text()
                        feed = feedparser.parse(text)
                        for entry in feed.entries[:5]: 
                            items.append({
                                "source": feed.feed.title if 'feed' in feed else url,
                                "title": entry.title,
                                "link": entry.link,
                                "published": entry.published if 'published' in entry else ""
                            })
                except Exception as e:
                    logger.warning(f"Error fetching {url}: {e}")
        return items

    async def get_daily_digest(self) -> Dict[str, Any]:
        """Generates a daily news digest."""
        items = await self.fetch_feeds()
        summary = f"Aggregated {len(items)} top stories from global sources. Topics include global politics, technology, and economic updates."
        return {
            "date": datetime.datetime.now().strftime("%Y-%m-%d"),
            "items": items[:10],
            "summary": summary
        }
