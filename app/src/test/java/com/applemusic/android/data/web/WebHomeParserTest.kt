package com.applemusic.android.data.web

import com.applemusic.android.data.model.AlbumDetail
import com.applemusic.android.data.model.ArtistDetail
import com.applemusic.android.data.model.HomeSectionStyle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WebHomeParserTest {

    // Estructura recortada de la respuesta real de amp-api.music.apple.com (format[resources]=map)
    private val body = """
    {
      "next": "/v1/me/recommendations?name=listen-now&offset=10",
      "data": [
        {"id": "19-a", "type": "personal-recommendation"},
        {"id": "15-b", "type": "personal-recommendation"},
        {"id": "15-c", "type": "personal-recommendation"},
        {"id": "15-d", "type": "personal-recommendation"},
        {"id": "18-e", "type": "personal-recommendation"},
        {"id": "24-f", "type": "personal-recommendation"},
        {"id": "11-g", "type": "personal-recommendation"}
      ],
      "resources": {
        "personal-recommendation": {
          "19-a": {"id": "19-a", "type": "personal-recommendation",
            "attributes": {"title": {"stringForDisplay": "Sugerencias destacadas para ti"},
                           "display": {"kind": "MusicNotesHeroShelf"}, "hasSeeAll": false},
            "relationships": {"contents": {"data": [
              {"id": "1769510705", "type": "albums", "meta": {"reason": {"stringForDisplay": "Volver a escuchar"}}},
              {"id": "ra.481488005", "type": "stations", "meta": {"editorialCard": "vcard", "reason": {"stringForDisplay": "Destacando a A${'$'}AP Rocky"}}},
              {"id": "ed.1", "type": "editorial-items"}
            ]}}},
          "15-b": {"id": "15-b", "type": "personal-recommendation",
            "attributes": {"title": {"stringForDisplay": "Similar a Stay - Single"},
                           "display": {"kind": "MusicCoverShelf"}, "hasSeeAll": true},
            "relationships": {"contents": {"data": [{"id": "l.o7f", "type": "library-albums"}]}}},
          "15-c": {"id": "15-c", "type": "personal-recommendation",
            "attributes": {"display": {"kind": "MusicSuperHeroShelf"}},
            "relationships": {"contents": {"data": [{"id": "ra.6834475371", "type": "stations"}]}}},
          "15-d": {"id": "15-d", "type": "personal-recommendation",
            "attributes": {"title": {"stringForDisplay": "Vacía"}, "display": {"kind": "MusicCoverShelf"}},
            "relationships": {"contents": {"data": []}}},
          "18-e": {"id": "18-e", "type": "personal-recommendation",
            "attributes": {"title": {"stringForDisplay": "Artistas favoritos"},
                           "display": {"kind": "MusicCircleCoverShelf"}, "hasSeeAll": false},
            "relationships": {"contents": {"data": [{"id": "art.1", "type": "artists"}]}}},
          "24-f": {"id": "24-f", "type": "personal-recommendation",
            "attributes": {"title": {"stringForDisplay": "Encuentra tu estado de ánimo"},
                           "display": {"kind": "MusicCoverGrid"}, "hasSeeAll": false},
            "relationships": {"contents": {"data": [{"id": "pl.mood1", "type": "playlists"}]}}},
          "11-g": {"id": "11-g", "type": "personal-recommendation",
            "attributes": {"title": {"stringForDisplay": "Tus amigos están escuchando"},
                           "display": {"kind": "MusicSocialCardShelf"}, "hasSeeAll": false},
            "relationships": {"contents": {"data": [{"id": "sp.1", "type": "social-profiles"}]}}}
        },
        "albums": {
          "1769510705": {"id": "1769510705", "type": "albums", "attributes": {
            "name": "Tu Boda - Single", "artistName": "Oscar Maydon", "contentRating": "explicit",
            "artwork": {"url": "https://x/{w}x{h}bb.{f}", "bgColor": "112233", "textColor1": "ffffff"},
            "editorialArtwork": {"superHeroTall": {"url": "https://tall/{w}x{h}sr.{f}", "bgColor": "445566"}}}},
          "999": {"id": "999", "type": "albums", "attributes": {
            "name": "OMAKASE", "artistName": "Álvaro Díaz", "artwork": {"url": "https://cat/{w}x{h}bb.{f}"}}}
        },
        "library-albums": {
          "l.o7f": {"id": "l.o7f", "type": "library-albums", "attributes": {"name": "OMAKASE"},
            "relationships": {"catalog": {"data": [{"id": "999", "type": "albums"}]}}}
        },
        "stations": {
          "ra.481488005": {"id": "ra.481488005", "type": "stations", "attributes": {
            "name": "A${'$'}AP Rocky y artistas similares", "artwork": {"url": "https://st/{w}x{h}cc.{f}"},
            "plainEditorialCard": {"vcard": {"editorialArtwork": {"superHeroTall": {"url": "https://card/{w}x{h}sr.{f}"}}}}}},
          "ra.6834475371": {"id": "ra.6834475371", "type": "stations", "attributes": {
            "name": "Westside Gunn", "artwork": {"url": "https://wg/{w}x{h}cc.{f}"},
            "editorialArtwork": {"superHeroWide": {"url": "https://wide/{w}x{h}sr.{f}"}}}}
        },
        "editorial-items": {
          "ed.1": {"id": "ed.1", "type": "editorial-items", "attributes": {
            "plainEditorialNotes": {"name": "Replay '26", "tagline": "Tus más escuchadas"},
            "editorialArtwork": {"superHeroTall": {"url": "https://rep/{w}x{h}sr.{f}"}}},
            "relationships": {"contents": {"data": [{"id": "pl.rep", "type": "playlists"}]}}}
        },
        "playlists": {
          "pl.rep": {"id": "pl.rep", "type": "playlists", "attributes": {
            "name": "Replay 2026", "curatorName": "Apple Music", "artwork": {"url": "https://pl/{w}x{h}bb.{f}"}}},
          "pl.mood1": {"id": "pl.mood1", "type": "playlists", "attributes": {
            "name": "Energía pura", "curatorName": "Apple Music", "artwork": {"url": "https://m1/{w}x{h}bb.{f}"}}}
        },
        "artists": {
          "art.1": {"id": "art.1", "type": "artists", "attributes": {
            "name": "Bad Bunny", "artwork": {"url": "https://bb/{w}x{h}cc.{f}"}}}
        },
        "social-profiles": {
          "sp.1": {"id": "sp.1", "type": "social-profiles", "attributes": {
            "name": "Sofía", "handle": "sofiamusic", "topGenreNames": ["Pop", "Indie"],
            "artwork": {"url": "https://sp/{w}x{h}cc.{f}"}}}
        }
      }
    }
    """.trimIndent()

    @Test
    fun parsesShelvesPaginationAndAllStyles() {
        val page = WebHomeParser.parsePage(body)
        assertEquals("/v1/me/recommendations?name=listen-now&offset=10", page.nextUrl)
        val sections = page.sections
        assertEquals(6, sections.size) // la vacía se omite

        val hero = sections[0]
        assertEquals(HomeSectionStyle.HERO_PICKS, hero.style)
        assertEquals("Sugerencias destacadas para ti", hero.title)
        val album = hero.items[0]
        assertEquals("Volver a escuchar", album.badge)
        assertEquals("Oscar Maydon", album.subtitle)
        assertTrue(album.isExplicit)
        assertEquals("https://tall/600x800sr.jpg", album.tallArtworkUrl)
        assertEquals("https://card/600x800sr.jpg", hero.items[1].tallArtworkUrl)

        val replayItem = hero.items[2]
        assertEquals("pl.rep", replayItem.id)
        assertEquals("playlists", replayItem.type)
        assertEquals("Tus más escuchadas", replayItem.badge)
        assertEquals("https://rep/600x800sr.jpg", replayItem.tallArtworkUrl)

        val moreLike = sections[1]
        assertEquals("Más como", moreLike.subtitle)
        assertEquals("Stay - Single", moreLike.title)
        assertTrue(moreLike.hasChevron)
        assertEquals("Álvaro Díaz", moreLike.items[0].subtitle)
        assertEquals("https://cat/600x600bb.jpg", moreLike.items[0].artworkUrl)
        assertNull(moreLike.items[0].tallArtworkUrl)

        val superHero = sections[2]
        assertEquals(HomeSectionStyle.SUPER_HERO, superHero.style)
        assertEquals("https://wide/1200x500sr.jpg", superHero.items[0].tallArtworkUrl)

        val circles = sections[3]
        assertEquals(HomeSectionStyle.ARTIST_CIRCLES, circles.style)
        assertEquals("Bad Bunny", circles.items[0].title)

        val grid = sections[4]
        assertEquals(HomeSectionStyle.COVER_GRID, grid.style)
        assertEquals("Energía pura", grid.items[0].title)

        val social = sections[5]
        assertEquals("Sofía", social.items[0].title)
        assertEquals("@sofiamusic · Pop · Indie", social.items[0].subtitle)
    }

    @Test
    fun parsesBadgingMapSocialProfilesAndPrefixFallbacks() {
        val badgingJson = """{"results":{"badgingMap":{"1769510705":["friend.1"]}}}"""
        val map = WebHomeParser.parseBadgingMap(badgingJson)
        assertEquals(listOf("friend.1"), map["1769510705"])

        val profileJson = """
            {"data":[{"id":"friend.1","type":"social-profiles","attributes":{
              "name":"Carlos","handle":"carlosm","artwork":{"url":"https://img/{w}x{h}cc.{f}"}
            }}]}
        """.trimIndent()
        val profiles = WebHomeParser.parseSocialProfiles(profileJson)
        assertEquals("Carlos", profiles["friend.1"]?.name)
        assertEquals("https://img/160x160cc.jpg", profiles["friend.1"]?.avatarUrl)

        val fallbackHome = """
            {
              "data": [
                {"id": "18-fav", "type": "personal-recommendation"},
                {"id": "24-mood", "type": "personal-recommendation"}
              ],
              "resources": {
                "personal-recommendation": {
                  "18-fav": {"id": "18-fav", "type": "personal-recommendation",
                    "attributes": {"title": {"stringForDisplay": "Artistas favoritos"}, "display": {"kind": "MusicCoverShelf"}},
                    "relationships": {"contents": {"data": [{"id": "art.1", "type": "artists"}]}}},
                  "24-mood": {"id": "24-mood", "type": "personal-recommendation",
                    "attributes": {"title": {"stringForDisplay": "Encuentra tu estado de ánimo"}, "display": {"kind": "MusicCoverShelf"}},
                    "relationships": {"contents": {"data": [{"id": "pl.1", "type": "playlists"}]}}}
                },
                "artists": {"art.1": {"id": "art.1", "type": "artists", "attributes": {"name": "Feid"}}},
                "playlists": {"pl.1": {"id": "pl.1", "type": "playlists", "attributes": {"name": "Chill"}}}
              }
            }
        """.trimIndent()
        val parsed = WebHomeParser.parse(fallbackHome)
        assertEquals(HomeSectionStyle.ARTIST_CIRCLES, parsed[0].style)
        assertEquals(HomeSectionStyle.COVER_GRID, parsed[1].style)
    }

    // Estructura de groupings: data -> tabs[0] -> children(shelves) -> contents/children
    @Test
    fun parsesNewTabGroupings() {
        val json = """
            {
              "data": [{"id": "grouping.music", "type": "groupings"}],
              "resources": {
                "groupings": {"grouping.music": {"id": "grouping.music", "type": "groupings",
                  "attributes": {"name": "Novedades"},
                  "relationships": {"tabs": {"data": [{"id": "tab.1", "type": "editorial-elements"}]}}}},
                "editorial-elements": {
                  "tab.1": {"id": "tab.1", "type": "editorial-elements",
                    "relationships": {"children": {"data": [
                      {"id": "shelf.hero", "type": "editorial-elements"},
                      {"id": "shelf.top", "type": "editorial-elements"},
                      {"id": "shelf.new", "type": "editorial-elements"},
                      {"id": "shelf.empty", "type": "editorial-elements"}
                    ]}}},
                  "shelf.hero": {"id": "shelf.hero", "type": "editorial-elements",
                    "attributes": {"name": "Destacado", "editorialElementKind": "320"},
                    "relationships": {"contents": {"data": [{"id": "a1", "type": "albums"}]}}},
                  "shelf.top": {"id": "shelf.top", "type": "editorial-elements",
                    "attributes": {"name": "Lo mejor de la semana", "editorialElementKind": "327"},
                    "relationships": {"contents": {"data": [{"id": "a2", "type": "albums"}]}}},
                  "shelf.new": {"id": "shelf.new", "type": "editorial-elements",
                    "attributes": {"name": "Estrenos", "editorialElementKind": "385"},
                    "relationships": {"children": {"data": [{"id": "pl1", "type": "playlists"}]}}},
                  "shelf.empty": {"id": "shelf.empty", "type": "editorial-elements",
                    "attributes": {"name": "Vacía", "editorialElementKind": "320"},
                    "relationships": {"contents": {"data": []}}}
                },
                "albums": {
                  "a1": {"id": "a1", "type": "albums", "attributes": {"name": "Disco 1", "artistName": "Artista 1",
                    "artwork": {"url": "https://a1/{w}x{h}bb.{f}"}}},
                  "a2": {"id": "a2", "type": "albums", "attributes": {"name": "Disco 2", "artistName": "Artista 2",
                    "artwork": {"url": "https://a2/{w}x{h}bb.{f}"}}}
                },
                "playlists": {
                  "pl1": {"id": "pl1", "type": "playlists", "attributes": {"name": "Lista 1", "curatorName": "Apple Music",
                    "artwork": {"url": "https://pl1/{w}x{h}bb.{f}"}}}
                }
              }
            }
        """.trimIndent()

        val sections = WebHomeParser.parseGroupings(json)
        assertEquals(3, sections.size) // la vacía se omite
        assertEquals(HomeSectionStyle.SUPER_HERO, sections[0].style)
        assertEquals("Destacado", sections[0].title)
        assertEquals("Disco 1", sections[0].items[0].title)
        assertEquals(HomeSectionStyle.COMPACT_TRACKS, sections[1].style)
        assertEquals("Disco 2", sections[1].items[0].title)
        assertEquals(HomeSectionStyle.HERO_PICKS, sections[2].style)
        assertEquals("Lista 1", sections[2].items[0].title)
    }

    @Test
    fun filtersOutPodcastsTvShowsAndRadioShowTalkStations() {
        val json = """
            {
              "data": [
                {"id": "99-mix", "type": "personal-recommendation"},
                {"id": "24-moods", "type": "personal-recommendation"},
                {"id": "18-fav", "type": "personal-recommendation"},
                {"id": "98-pod", "type": "personal-recommendation"}
              ],
              "resources": {
                "personal-recommendation": {
                  "99-mix": {
                    "id": "99-mix",
                    "type": "personal-recommendation",
                    "attributes": {"title": {"stringForDisplay": "Mix"}, "display": {"kind": "MusicCoverShelf"}},
                    "relationships": {"contents": {"data": [
                      {"id": "123", "type": "albums"},
                      {"id": "pod.1", "type": "podcasts"},
                      {"id": "ep.1", "type": "podcast-episodes"},
                      {"id": "tv.1", "type": "tv-shows"},
                      {"id": "ra.talk", "type": "stations"}
                    ]}}
                  },
                  "24-moods": {
                    "id": "24-moods",
                    "type": "personal-recommendation",
                    "attributes": {"title": {"stringForDisplay": "Encuentra tu estado de ánimo"}, "display": {"kind": "MusicCoverShelf"}},
                    "relationships": {"contents": {"data": [{"id": "ra.mood1", "type": "stations"}]}}
                  },
                  "18-fav": {
                    "id": "18-fav",
                    "type": "personal-recommendation",
                    "attributes": {"title": {"stringForDisplay": "Artistas favoritos"}, "display": {"kind": "MusicCoverShelf"}},
                    "relationships": {"contents": {"data": [{"id": "ra.art1", "type": "stations"}]}}
                  },
                  "98-pod": {
                    "id": "98-pod",
                    "type": "personal-recommendation",
                    "attributes": {"display": {"kind": "MusicSuperHeroShelf"}},
                    "relationships": {"contents": {"data": [{"id": "ra.talk", "type": "stations"}]}}
                  }
                },
                "albums": {"123": {"id": "123", "type": "albums", "attributes": {"name": "Álbum válido"}}},
                "podcasts": {"pod.1": {"id": "pod.1", "type": "podcasts", "attributes": {"name": "Un Podcast"}}},
                "podcast-episodes": {"ep.1": {"id": "ep.1", "type": "podcast-episodes", "attributes": {"name": "Episodio 1"}}},
                "tv-shows": {"tv.1": {"id": "tv.1", "type": "tv-shows", "attributes": {"name": "Show TV"}}},
                "stations": {
                  "ra.talk": {
                    "id": "ra.talk",
                    "type": "stations",
                    "attributes": {"name": "Show de radio"},
                    "relationships": {"radio-show": {"data": [{"id": "rs.1", "type": "radio-shows"}]}}
                  },
                  "ra.mood1": {
                    "id": "ra.mood1",
                    "type": "stations",
                    "attributes": {"name": "Buen humor"},
                    "relationships": {"radio-show": {"data": []}}
                  },
                  "ra.art1": {
                    "id": "ra.art1",
                    "type": "stations",
                    "attributes": {"name": "Wisin & Yandel"},
                    "relationships": {"radio-show": {"data": []}}
                  }
                }
              }
            }
        """.trimIndent()

        val sections = WebHomeParser.parse(json)
        assertEquals(3, sections.size)
        assertEquals(1, sections[0].items.size)
        assertEquals("123", sections[0].items[0].id)
        assertEquals(HomeSectionStyle.COVER_GRID, sections[1].style)
        assertEquals("ra.mood1", sections[1].items[0].id)
        assertEquals(HomeSectionStyle.ARTIST_CIRCLES, sections[2].style)
        assertEquals("ra.art1", sections[2].items[0].id)
    }

    @Test
    fun parsesNovedadesHeroBannersLinksAndCatalogCharts() {
        val groupingsJson = """
            {
              "data": [{"id": "g.1", "type": "groupings"}],
              "resources": {
                "groupings": {
                  "g.1": {
                    "id": "g.1",
                    "type": "groupings",
                    "relationships": {"tabs": {"data": [{"id": "t.1", "type": "editorial-elements"}]}}
                  }
                },
                "editorial-elements": {
                  "t.1": {
                    "id": "t.1",
                    "type": "editorial-elements",
                    "relationships": {"children": {"data": [
                      {"id": "s.hero316", "type": "editorial-elements"},
                      {"id": "s.genres322", "type": "editorial-elements"},
                      {"id": "s.more391", "type": "editorial-elements"}
                    ]}}
                  },
                  "s.hero316": {
                    "id": "s.hero316",
                    "type": "editorial-elements",
                    "attributes": {"editorialElementKind": "316"},
                    "relationships": {"children": {"data": [{"id": "el.card1", "type": "editorial-elements"}]}}
                  },
                  "el.card1": {
                    "id": "el.card1",
                    "type": "editorial-elements",
                    "attributes": {"designBadge": "NUEVO ÁLBUM", "editorialElementKind": "317"},
                    "relationships": {"contents": {"data": [{"id": "alb.99", "type": "albums"}]}}
                  },
                  "s.genres322": {
                    "id": "s.genres322",
                    "type": "editorial-elements",
                    "attributes": {
                      "editorialElementKind": "322",
                      "links": [
                        {"label": "Pop", "url": "https://music.apple.com/WebObjects/MZStore.woa/wa/viewGrouping?cc=mx&id=172111"}
                      ]
                    }
                  },
                  "s.more391": {
                    "id": "s.more391",
                    "type": "editorial-elements",
                    "attributes": {
                      "name": "Más para explorar",
                      "editorialElementKind": "391",
                      "links": [
                        {"label": "Charts", "target": "internal", "url": "https://itunes.apple.com/WebObjects/MZStore.woa/wa/viewTop?genreId=34"}
                      ]
                    }
                  }
                },
                "albums": {
                  "alb.99": {
                    "id": "alb.99",
                    "type": "albums",
                    "attributes": {
                      "name": "ATRAVESAO",
                      "artistName": "Rey Quinto",
                      "artwork": {"url": "https://art/{w}x{h}bb.{f}"},
                      "editorialArtwork": {
                        "subscriptionHero": {"width": 4320, "height": 1080, "url": "https://hero/{w}x{h}{c}.{f}"}
                      }
                    }
                  }
                }
              }
            }
        """.trimIndent()

        val sections = WebHomeParser.parseGroupings(groupingsJson)
        assertEquals(2, sections.size) // 322 sin título se omite aquí; queda 316 + 391
        assertEquals(HomeSectionStyle.SUPER_HERO, sections[0].style)
        assertEquals("ATRAVESAO", sections[0].items[0].title)
        assertEquals("https://hero/1200x300bb.jpg", sections[0].items[0].tallArtworkUrl)
        assertEquals("NUEVO ÁLBUM", sections[0].items[0].badge)
        assertEquals("albums", sections[0].items[0].type)
        assertEquals(HomeSectionStyle.EXPLORE_LINKS, sections[1].style)
        assertEquals("Charts", sections[1].items[0].title)
        assertEquals("editorial-link", sections[1].items[0].type)

        val genreSections = WebHomeParser.parseGenreLinksFromGroupings(groupingsJson)
        assertEquals(1, genreSections.size)
        assertEquals("Pop", genreSections[0].items[0].title)

        val chartsJson = """
            {
              "results": {
                "songs": [
                  {"name": "Top Canciones", "chart": "most-played", "data": [
                    {"id": "s.1", "type": "songs", "attributes": {"name": "Canción 1", "artistName": "Artista 1", "artwork": {"url": "https://s/{w}x{h}bb.{f}"}}}
                  ]}
                ],
                "albums": [
                  {"name": "Top Álbumes", "chart": "most-played-albums", "data": [
                    {"id": "a.1", "type": "albums", "attributes": {"name": "Álbum 1", "artistName": "Artista 1", "artwork": {"url": "https://a/{w}x{h}bb.{f}"}}}
                  ]}
                ]
              }
            }
        """.trimIndent()

        val charts = WebHomeParser.parseCatalogCharts(chartsJson)
        assertEquals(2, charts.size)
        assertEquals(HomeSectionStyle.COMPACT_TRACKS, charts[0].style)
        assertEquals("Top Canciones", charts[0].title)
        assertEquals(HomeSectionStyle.SQUARE_CAROUSEL, charts[1].style)
        assertEquals("Top Álbumes", charts[1].title)

        val roomJson = """
            {
              "data": [
                {
                  "id": "6456176471",
                  "type": "rooms",
                  "attributes": {"title": "Décadas"},
                  "relationships": {
                    "contents": {
                      "next": "/v1/editorial/mx/rooms/6456176471/contents?offset=10",
                      "data": [
                        {
                          "id": "1558256771",
                          "type": "apple-curators",
                          "attributes": {"name": "Años 90", "artwork": {"url": "https://d/{w}x{h}bb.{f}"}}
                        }
                      ]
                    }
                  }
                }
              ]
            }
        """.trimIndent()
        val (roomItems, roomNext) = WebHomeParser.parseEditorialRoom(roomJson, "Décadas")
        assertEquals(1, roomItems.size)
        assertEquals("1558256771", roomItems[0].id)
        assertEquals("apple-curators", roomItems[0].type)
        assertEquals("Años 90", roomItems[0].title)
        assertEquals("/v1/editorial/mx/rooms/6456176471/contents?offset=10", roomNext)

        val curatorPlaylistsJson = """
            {
              "data": [
                {
                  "id": "pl.90s",
                  "type": "playlists",
                  "attributes": {"name": "Lo mejor de los 90", "curatorName": "Apple Music", "artwork": {"url": "https://p/{w}x{h}bb.{f}"}}
                }
              ]
            }
        """.trimIndent()
        val curatorSections = WebHomeParser.parseCuratorSections(curatorPlaylistsJson, "Años 90")
        assertEquals(1, curatorSections.size)
        assertEquals("pl.90s", curatorSections[0].items[0].id)
    }

    @Test
    fun parsesRadioTabGroupingsRoomsAndCuratorGrouping() {
        val radioJson = """
            {
              "data": [{"id": "g.radio", "type": "groupings"}],
              "resources": {
                "groupings": {
                  "g.radio": {
                    "id": "g.radio",
                    "type": "groupings",
                    "relationships": {"tabs": {"data": [{"id": "tab.radio", "type": "editorial-elements"}]}}
                  }
                },
                "editorial-elements": {
                  "tab.radio": {
                    "id": "tab.radio",
                    "type": "editorial-elements",
                    "relationships": {"children": {"data": [
                      {"id": "s.hero316", "type": "editorial-elements"},
                      {"id": "s.onair488", "type": "editorial-elements"},
                      {"id": "s.ep326", "type": "editorial-elements"},
                      {"id": "s.links391", "type": "editorial-elements"}
                    ]}}
                  },
                  "s.hero316": {
                    "id": "s.hero316",
                    "type": "editorial-elements",
                    "attributes": {"editorialElementKind": "316"},
                    "relationships": {"children": {"data": [{"id": "el.am1", "type": "editorial-elements"}]}}
                  },
                  "el.am1": {
                    "id": "el.am1",
                    "type": "editorial-elements",
                    "attributes": {"editorialElementKind": "383", "designBadge": "EN EXCLUSIVA"},
                    "relationships": {"contents": {"data": [{"id": "ra.1", "type": "stations"}]}}
                  },
                  "s.onair488": {
                    "id": "s.onair488",
                    "type": "editorial-elements",
                    "attributes": {"editorialElementKind": "488", "title": "Al aire ahora"},
                    "relationships": {"children": {"data": [{"id": "el.489", "type": "editorial-elements"}]}}
                  },
                  "el.489": {
                    "id": "el.489",
                    "type": "editorial-elements",
                    "attributes": {"editorialElementKind": "489"},
                    "relationships": {"contents": {"data": [{"id": "ra.1", "type": "stations"}]}}
                  },
                  "s.ep326": {
                    "id": "s.ep326",
                    "type": "editorial-elements",
                    "attributes": {"editorialElementKind": "326", "name": "Episodios de radio más recientes"},
                    "relationships": {"contents": {"data": [{"id": "ra.ep", "type": "stations"}]}}
                  },
                  "s.links391": {
                    "id": "s.links391",
                    "type": "editorial-elements",
                    "attributes": {
                      "editorialElementKind": "391",
                      "name": "Más para explorar",
                      "links": [
                        {"label": "Acústica", "url": "https://itunes.apple.com/mx/collection/acustica/id171952?fcId=6737578234&mt=14"}
                      ]
                    }
                  }
                },
                "stations": {
                  "ra.1": {
                    "id": "ra.1",
                    "type": "stations",
                    "attributes": {
                      "name": "Apple Music 1",
                      "isLive": true,
                      "editorialArtwork": {
                        "superHeroWide": {"width": 4320, "height": 1800, "url": "https://wide/{w}x{h}{c}.{f}"},
                        "backgroundStatic1x1": {"width": 3840, "height": 3840, "url": "https://sq/{w}x{h}bb.{f}"}
                      }
                    },
                    "relationships": {
                      "events": {"data": [{"id": "ev.1", "type": "station-events"}]}
                    }
                  },
                  "ra.ep": {
                    "id": "ra.ep",
                    "type": "stations",
                    "attributes": {"name": "Paloma Morphy", "artwork": {"url": "https://ep/{w}x{h}bb.{f}"}},
                    "relationships": {"radio-show": {"data": [{"id": "rs.1", "type": "radio-shows"}]}}
                  }
                },
                "station-events": {
                  "ev.1": {
                    "id": "ev.1",
                    "type": "station-events",
                    "attributes": {"title": "The Zane Lowe Show"}
                  }
                }
              }
            }
        """.trimIndent()

        val radioSections = WebHomeParser.parseGroupings(radioJson, filterRadioTalkStations = false)
        assertEquals(4, radioSections.size)
        assertEquals(HomeSectionStyle.SUPER_HERO, radioSections[0].style)
        assertEquals("Apple Music 1", radioSections[0].items[0].title)
        assertEquals("https://wide/1200x500bb.jpg", radioSections[0].items[0].tallArtworkUrl)
        assertEquals("The Zane Lowe Show", radioSections[0].items[0].subtitle)

        assertEquals(HomeSectionStyle.SQUARE_CAROUSEL, radioSections[1].style)
        assertEquals("Al aire ahora", radioSections[1].title)
        assertEquals("EN VIVO", radioSections[1].items[0].badge)
        assertEquals("https://sq/600x600bb.jpg", radioSections[1].items[0].artworkUrl)

        assertEquals("Episodios de radio más recientes", radioSections[2].title)
        assertEquals("Paloma Morphy", radioSections[2].items[0].title)

        assertEquals(HomeSectionStyle.EXPLORE_LINKS, radioSections[3].style)
        assertEquals("Acústica", radioSections[3].items[0].title)

        val roomContentsJson = """
            {
              "data": [
                {"id": "ra.985", "type": "stations", "attributes": {"name": "Estación acústica", "artwork": {"url": "https://st/{w}x{h}bb.{f}"}}}
              ]
            }
        """.trimIndent()
        val (roomItems, _) = WebHomeParser.parseEditorialRoom(roomContentsJson, "Acústica")
        assertEquals(1, roomItems.size)
        assertEquals("Estación acústica", roomItems[0].title)

        val curatorJson = """
            {
              "data": [
                {
                  "id": "1082540962",
                  "type": "apple-curators",
                  "relationships": {"grouping": {"data": [{"id": "171449", "type": "groupings"}]}}
                }
              ]
            }
        """.trimIndent()
        assertEquals("171449", WebHomeParser.extractCuratorGroupingId(curatorJson))
    }

    @Test
    fun parsesSearchResultsSuggestionsLibraryListsAndLibraryArtists() {
        val suggestionsJson = """
            {
              "results": {
                "suggestions": [
                  {"kind": "terms", "displayTerm": "bad bunny"},
                  {"kind": "topResults", "content": {"id": "art.bb", "type": "artists", "attributes": {"name": "Bad Bunny", "artwork": {"url": "https://bb/{w}x{h}bb.{f}"}}}}
                ]
              }
            }
        """.trimIndent()
        val sug = WebHomeParser.parseSearchSuggestions(suggestionsJson)
        assertEquals(listOf("bad bunny"), sug.terms)
        assertEquals("Bad Bunny", sug.topResults[0].title)

        val searchJson = """
            {
              "results": {
                "topResults": {"data": [
                  {"id": "art.bb", "type": "artists", "attributes": {"name": "Bad Bunny"}}
                ]},
                "songs": {"data": [
                  {"id": "s.1", "type": "songs", "attributes": {"name": "Tití Me Preguntó", "artistName": "Bad Bunny", "artwork": {"url": "https://s/{w}x{h}bb.{f}"}}}
                ]},
                "artists": {"data": [
                  {"id": "art.bb", "type": "artists", "attributes": {"name": "Bad Bunny"}}
                ]},
                "albums": {"data": [
                  {"id": "alb.1", "type": "albums", "attributes": {"name": "Un Verano Sin Ti", "artistName": "Bad Bunny"}}
                ]}
              }
            }
        """.trimIndent()
        val results = WebHomeParser.parseSearchResults(searchJson)
        assertEquals(4, results.size)
        assertEquals("Resultado principal", results[0].title)
        assertEquals(HomeSectionStyle.COMPACT_TRACKS, results[1].style)
        assertEquals("Canciones", results[1].title)
        assertEquals(HomeSectionStyle.ARTIST_CIRCLES, results[2].style)
        assertEquals("Artistas", results[2].title)
        assertEquals(HomeSectionStyle.SQUARE_CAROUSEL, results[3].style)

        val libJson = """
            {
              "next": "/v1/me/library/artists?offset=100",
              "data": [
                {
                  "id": "r.12345",
                  "type": "library-artists",
                  "attributes": {"name": "Peso Pluma"},
                  "relationships": {
                    "catalog": {"data": [{"id": "1500139475", "type": "artists", "attributes": {"artwork": {"url": "https://pp/{w}x{h}bb.{f}"}}}]}
                  }
                }
              ]
            }
        """.trimIndent()
        val (libItems, nextUrl) = WebHomeParser.parseLibraryList(libJson)
        assertEquals("/v1/me/library/artists?offset=100", nextUrl)
        assertEquals(1, libItems.size)
        assertEquals("1500139475", libItems[0].catalogId)
        assertEquals("https://pp/600x600bb.jpg", libItems[0].artworkUrl)

        val libArtistDetailJson = """
            {
              "data": [
                {
                  "id": "r.999",
                  "type": "library-artists",
                  "attributes": {"name": "Artista Local"},
                  "relationships": {
                    "albums": {"data": [{"id": "l.1", "type": "library-albums", "attributes": {"name": "Disco Local", "artistName": "Artista Local", "artwork": {"url": "https://loc/{w}x{h}bb.{f}"}}}]}
                  }
                }
              ]
            }
        """.trimIndent()
        val (catId, fallbackArtist) = WebHomeParser.extractLibraryArtistDetail(libArtistDetailJson, "Artista Local")
        assertNull(catId)
        assertEquals("Artista Local", fallbackArtist?.name)
        assertEquals(1, fallbackArtist?.albums?.size)
    }

    @Test
    fun parsesSearchLandingArtworkBatchAndFullArtistDetail() {
        val searchLandingJson = """
            {
              "data": [{"id": "rec.1", "type": "personal-recommendation"}],
              "resources": {
                "personal-recommendation": {
                  "rec.1": {
                    "id": "rec.1",
                    "type": "personal-recommendation",
                    "relationships": {
                      "contents": {"data": [
                        {"id": "1531542847", "type": "apple-curators"},
                        {"id": "1514706977", "type": "editorial-items"}
                      ]}
                    }
                  }
                },
                "apple-curators": {
                  "1531542847": {
                    "id": "1531542847",
                    "type": "apple-curators",
                    "attributes": {
                      "name": "Apple Music Latinoamérica",
                      "artwork": {"width": 1080, "height": 1080, "url": "https://cat/{w}x{h}{c}.{f}", "bgColor": "e0115f"}
                    }
                  }
                },
                "editorial-items": {
                  "1514706977": {
                    "id": "1514706977",
                    "type": "editorial-items",
                    "attributes": {
                      "plainEditorialNotes": {"name": "Charts"},
                      "link": {"target": "internal", "url": "https://itunes.apple.com/WebObjects/MZStore.woa/wa/viewTop?genreId=34"},
                      "editorialArtwork": {
                        "brandLogo": {"width": 1080, "height": 1080, "url": "https://charts/{w}x{h}{c}.{f}", "bgColor": "474818"}
                      }
                    }
                  }
                }
              }
            }
        """.trimIndent()
        val categories = WebHomeParser.parseSearchLandingCategories(searchLandingJson)
        assertEquals(2, categories.size)
        assertEquals("Latinoamérica", categories[0].title)
        assertEquals("https://cat/600x600bb.jpg", categories[0].artworkUrl)
        assertEquals("Charts", categories[1].title)
        assertEquals("editorial-link", categories[1].type)
        assertEquals("https://charts/600x600bb.jpg", categories[1].artworkUrl)

        val artistBatchJson = """
            {
              "data": [
                {
                  "id": "1126808565",
                  "type": "artists",
                  "attributes": {
                    "name": "Bad Bunny",
                    "editorialArtwork": {"backgroundStatic1x1": {"url": "https://bg/{w}x{h}{c}.{f}", "bgColor": "112233"}}
                  }
                }
              ]
            }
        """.trimIndent()
        val artMap = WebHomeParser.parseArtistArtworkBatch(artistBatchJson)
        assertEquals("https://bg/600x600cc.jpg", artMap["1126808565"]?.first)
        assertEquals("1126808565".let { "112233" }, artMap["1126808565"]?.second)

        val fullArtistJson = """
            {
              "data": [
                {
                  "id": "1126808565",
                  "type": "artists",
                  "attributes": {
                    "name": "Bad Bunny",
                    "genreNames": ["Urbano latino"],
                    "origin": "Vega Baja, Puerto Rico",
                    "bornOrFormed": "10 de marzo de 1994",
                    "artistBio": "<B>Benito</B><br>Artista global.",
                    "artwork": {"url": "https://sq/{w}x{h}{c}.{f}"},
                    "editorialArtwork": {"storeFlowcase": {"width": 4320, "height": 1080, "url": "https://hero/{w}x{h}{c}.{f}"}}
                  },
                  "views": {
                    "latest-release": {"data": [{"id": "alb.last", "type": "albums", "attributes": {"name": "DeBÍ TiRAR MáS FOToS"}}]},
                    "top-songs": {"data": [{"id": "s.1", "type": "songs", "attributes": {"name": "BAILE INoLVIDABLE", "artistName": "Bad Bunny"}}]},
                    "featured-albums": {"data": [{"id": "alb.feat", "type": "albums", "attributes": {"name": "Un Verano Sin Ti"}}]},
                    "full-albums": {"data": [{"id": "alb.1", "type": "albums", "attributes": {"name": "YHLQMDLG"}}]},
                    "singles": {"data": [{"id": "alb.s1", "type": "albums", "attributes": {"name": "Callaita - Single"}}]},
                    "live-albums": {"data": [{"id": "alb.live", "type": "albums", "attributes": {"name": "En Vivo"}}]},
                    "compilation-albums": {"data": [{"id": "alb.comp", "type": "albums", "attributes": {"name": "Compilación"}}]},
                    "appears-on-albums": {"data": [{"id": "alb.app", "type": "albums", "attributes": {"name": "OASIS"}}]},
                    "featured-playlists": {"data": [{"id": "pl.1", "type": "playlists", "attributes": {"name": "Bad Bunny Essentials"}}]},
                    "top-music-videos": {"data": [{"id": "mv.1", "type": "music-videos", "attributes": {"name": "Moscow Mule"}}]},
                    "similar-artists": {"data": [{"id": "art.2", "type": "artists", "attributes": {"name": "Rauw Alejandro", "artwork": {"url": "https://sim/{w}x{h}{c}.{f}"}}}]}
                  }
                }
              ]
            }
        """.trimIndent()
        val detail = ArtistDetail.parse(fullArtistJson)!!
        assertEquals("Bad Bunny", detail.name)
        assertEquals("Vega Baja, Puerto Rico", detail.origin)
        assertEquals("10 de marzo de 1994", detail.bornOrFormed)
        assertEquals("Benito\nArtista global.", detail.bio)
        assertEquals("https://hero/1400x350bb.jpg", detail.heroUrl)
        assertEquals(1, detail.featuredAlbums.size)
        assertEquals(1, detail.liveAlbums.size)
        assertEquals(1, detail.compilationAlbums.size)
        assertEquals(1, detail.appearsOnAlbums.size)
        assertEquals(1, detail.playlists.size)
        assertEquals(1, detail.musicVideos.size)
        assertEquals("https://sim/600x600cc.jpg", detail.similarArtists[0].artworkUrl)

        val albumViewsJson = """
            {
              "data": [
                {
                  "id": "6835250277",
                  "type": "albums",
                  "attributes": {"name": "Way To Go", "artistName": "Empire Of The Sun"},
                  "relationships": {
                    "artists": {"data": [{"id": "275029873", "type": "artists"}]},
                    "tracks": {"data": [{"id": "t.1", "type": "songs", "attributes": {"name": "Way To Go", "artistName": "Empire Of The Sun"}}]}
                  },
                  "views": {
                    "more-by-artist": {"attributes": {"title": "Más de Empire Of The Sun"}, "data": [
                      {"id": "a.2", "type": "albums", "attributes": {"name": "Ice On The Dune", "artistName": "Empire Of The Sun"}}
                    ]},
                    "appears-on": {"attributes": {"title": "Aparece en"}, "data": [
                      {"id": "a.3", "type": "albums", "attributes": {"name": "Experts Only"}}
                    ]},
                    "you-might-also-like": {"attributes": {"title": "Quizás también te interese"}, "data": [
                      {"id": "a.4", "type": "albums", "attributes": {"name": "MGMT"}}
                    ]}
                  }
                }
              ]
            }
        """.trimIndent()
        val albumDetail = AlbumDetail.parse(albumViewsJson)!!
        assertEquals("275029873", albumDetail.primaryArtistId)
        assertEquals(3, albumDetail.relatedSections.size)
        assertEquals("Más de Empire Of The Sun", albumDetail.relatedSections[0].title)
        assertEquals("Aparece en", albumDetail.relatedSections[1].title)
        assertEquals("Quizás también te interese", albumDetail.relatedSections[2].title)

        val playlistViewsJson = """
            {
              "data": [
                {
                  "id": "pl.123",
                  "type": "playlists",
                  "attributes": {"name": "La Oficial", "curatorName": "Apple Music Música mexicana"},
                  "relationships": {
                    "curator": {"data": [{"id": "989002201", "type": "apple-curators", "attributes": {"name": "Apple Music Música mexicana"}}]},
                    "tracks": {"data": []}
                  },
                  "views": {
                    "featured-artists": {"attributes": {"title": "Artistas destacados"}, "data": [
                      {"id": "art.1", "type": "artists", "attributes": {"name": "Peso Pluma"}}
                    ]},
                    "more-by-curator": {"attributes": {"title": "Más de Apple Music Música mexicana"}, "data": [
                      {"id": "pl.2", "type": "playlists", "attributes": {"name": "Éxitos"}}
                    ]}
                  }
                }
              ]
            }
        """.trimIndent()
        val plDetail = AlbumDetail.parse(playlistViewsJson)!!
        assertEquals("989002201", plDetail.curatorItem?.id)
        assertEquals(2, plDetail.relatedSections.size)
        assertEquals(HomeSectionStyle.ARTIST_CIRCLES, plDetail.relatedSections[0].style)
        assertEquals("Artistas destacados", plDetail.relatedSections[0].title)
        assertEquals("Más de Apple Music Música mexicana", plDetail.relatedSections[1].title)
    }

    @Test
    fun `TtmlLyricsParser parses synced syllable lyrics and songwriters`() {
        val body = """
            {
              "data": [
                {
                  "id": "12345",
                  "type": "syllable-lyrics",
                  "attributes": {
                    "ttml": "<tt xmlns=\"http://www.w3.org/ns/ttml\"><head><metadata><iTunesMetadata><songwriters><songwriter>Tito Double P</songwriter></songwriters></iTunesMetadata></metadata></head><body><div><p begin=\"12.500s\" end=\"16.000s\"><span begin=\"12.500s\" end=\"13.200s\">Hola </span><span begin=\"13.250s\" end=\"16.000s\">mundo &amp; amigos</span></p><p begin=\"0:16.500\" end=\"0:20.000\">Segunda línea</p></div></body></tt>"
                  }
                }
              ]
            }
        """.trimIndent()
        val parsed = com.applemusic.android.data.model.TtmlLyricsParser.parseLyricsResponse("12345", body)!!
        assertEquals(true, parsed.isSynced)
        assertEquals(2, parsed.lines.size)
        assertEquals(12500L, parsed.lines[0].startMs)
        assertEquals(16000L, parsed.lines[0].endMs)
        assertEquals("Hola mundo & amigos", parsed.lines[0].text)
        assertEquals(2, parsed.lines[0].words.size)
        assertEquals(16500L, parsed.lines[1].startMs)
        assertEquals("Tito Double P", parsed.songwriterCredit)
    }

    @Test
    fun `MediaDetailsParser parses song credits audioTraits and account status`() {
        val creditsJson = """
            {
              "data": [
                {
                  "id": "999",
                  "type": "songs",
                  "attributes": {
                    "name": "EL LOKERON",
                    "artistName": "Tito Double P",
                    "albumName": "INCÓMODO",
                    "releaseDate": "2024-08-22",
                    "audioTraits": ["lossless", "atmos"],
                    "lyricsExcerpt": "Verso de muestra"
                  },
                  "relationships": {
                    "albums": {"data": [{"id": "alb.1", "type": "albums"}]},
                    "artists": {"data": [{"id": "art.1", "type": "artists"}]},
                    "credits": {"data": [{"id": "cred.1", "type": "credits"}]}
                  }
                }
              ],
              "resources": {
                "credits": {
                  "cred.1": {
                    "id": "cred.1",
                    "type": "credits",
                    "attributes": {"title": "Composición y Letras"},
                    "relationships": {
                      "credit-artists": {"data": [{"id": "ca.1", "type": "credit-artists"}]}
                    }
                  }
                },
                "credit-artists": {
                  "ca.1": {
                    "id": "ca.1",
                    "type": "credit-artists",
                    "attributes": {
                      "name": "Jesús Roberto Laija García",
                      "roleNames": ["Compositor", "Productor"]
                    },
                    "relationships": {
                      "artists": {"data": [{"id": "art.1", "type": "artists"}]}
                    }
                  }
                }
              }
            }
        """.trimIndent()
        val credits = com.applemusic.android.data.model.MediaDetailsParser.parseSongCredits(creditsJson)!!
        assertEquals("EL LOKERON", credits.title)
        assertEquals("alb.1", credits.albumId)
        assertEquals("art.1", credits.primaryArtistId)
        assertEquals(listOf("Lossless", "Dolby Atmos"), credits.audioTraits)
        assertEquals(1, credits.groups.size)
        assertEquals("Jesús Roberto Laija García", credits.groups[0].people[0].name)

        val statusJson = """
            {
              "data": [
                {
                  "id": "999",
                  "type": "songs",
                  "attributes": {"personalRating": 1, "inLibrary": true},
                  "relationships": {
                    "library": {"data": [{"id": "i.abc", "type": "library-songs"}]},
                    "albums": {"data": [{"id": "alb.1", "type": "albums"}]},
                    "artists": {"data": [{"id": "art.1", "type": "artists"}]}
                  }
                }
              ]
            }
        """.trimIndent()
        val status = com.applemusic.android.data.model.MediaDetailsParser.parseItemStatus("999", statusJson)!!
        assertEquals(true, status.isFavorite)
        assertEquals(true, status.inLibrary)
        assertEquals("i.abc", status.libraryId)
        assertEquals("alb.1", status.albumId)
    }

    @Test
    fun `parseSearchResults extracts unrestricted types including videos movies labels and social profiles`() {
        val searchJson = """
            {
              "results": {
                "music-videos": {"data": [{"id": "mv.1", "type": "music-videos", "attributes": {"name": "Clip Oficial", "artistName": "Tito Double P"}}]},
                "music-movies": {"data": [{"id": "mm.1", "type": "music-movies", "attributes": {"name": "Documental", "curatorName": "Apple Music"}}]},
                "record-labels": {"data": [{"id": "rl.1", "type": "record-labels", "attributes": {"name": "Double P Records"}}]},
                "social-profiles": {"data": [{"id": "sp.1", "type": "social-profiles", "attributes": {"name": "Jezra", "handle": "jezra_mx"}}]}
              }
            }
        """.trimIndent()
        val sections = WebHomeParser.parseSearchResults(searchJson)
        assertEquals(4, sections.size)
        assertEquals("Videos musicales", sections[0].title)
        assertEquals("Películas musicales", sections[1].title)
        assertEquals("Perfiles", sections[2].title)
        assertEquals(HomeSectionStyle.ARTIST_CIRCLES, sections[2].style)
        assertEquals("@jezra_mx", sections[2].items[0].subtitle)
        assertEquals("Sellos discográficos", sections[3].title)
    }

    @Test
    fun `TtmlLyricsParser handles nested x-bg spans timing None and seq container`() {
        val ttmlNested = """
            <tt xmlns="http://www.w3.org/ns/ttml" xmlns:ttm="http://www.w3.org/ns/ttml#metadata" xmlns:itunes="http://itunes.apple.com/lyric-ttml-extensions" itunes:timing="Word">
              <body>
                <div begin="10.000s" timeContainer="seq">
                  <p begin="2.000s" end="6.000s"><span begin="2.000s" end="3.500s">Hola </span><span ttm:role="x-bg"><span begin="3.600s" end="5.500s">(coro)</span></span></p>
                </div>
              </body>
            </tt>
        """.trimIndent()
        val parsed = com.applemusic.android.data.model.TtmlLyricsParser.parseTtml("s1", ttmlNested)!!
        assertEquals(true, parsed.isSynced)
        assertEquals(1, parsed.lines.size)
        assertEquals(12000L, parsed.lines[0].startMs)
        assertEquals(16000L, parsed.lines[0].endMs)
        assertEquals("Hola", parsed.lines[0].text)
        assertEquals("(coro)", parsed.lines[0].backgroundText)
        assertEquals(1, parsed.lines[0].words.size)
        assertEquals(1, parsed.lines[0].backgroundWords.size)
        assertEquals(13600L, parsed.lines[0].backgroundWords[0].startMs)

        val ttmlNone = """
            <tt xmlns="http://www.w3.org/ns/ttml" xmlns:itunes="http://itunes.apple.com/lyric-ttml-extensions" itunes:timing="None">
              <body><div><p>Sin sincronía</p></div></body>
            </tt>
        """.trimIndent()
        val parsedNone = com.applemusic.android.data.model.TtmlLyricsParser.parseTtml("s2", ttmlNone)!!
        assertEquals(false, parsedNone.isSynced)
        assertEquals("Sin sincronía", parsedNone.lines[0].text)
    }

    @Test
    fun `AlbumDetail parse preserves library track catalogId from relationship and playParams`() {
        val libPlaylistJson = """
            {
              "data": [
                {
                  "id": "p.lib1",
                  "type": "library-playlists",
                  "attributes": {"name": "Favoritas"},
                  "relationships": {
                    "tracks": {
                      "data": [
                        {
                          "id": "i.trk1",
                          "type": "library-songs",
                          "attributes": {
                            "name": "Canción 1",
                            "artistName": "Artista A",
                            "durationInMillis": 180000,
                            "playParams": {"id": "i.trk1", "catalogId": "987654321"}
                          }
                        }
                      ]
                    }
                  }
                }
              ]
            }
        """.trimIndent()
        val detail = AlbumDetail.parse(libPlaylistJson)!!
        assertEquals(1, detail.tracks.size)
        assertEquals("i.trk1", detail.tracks[0].id)
        assertEquals("987654321", detail.tracks[0].catalogId)
    }

    @Test
    fun `AlbumDetail and ArtistDetail parse editorialVideo motion loops and musicContentColorLogoTrimmed`() {
        val albumJson = """
            {
              "data": [
                {
                  "id": "1750307020",
                  "type": "albums",
                  "attributes": {
                    "name": "Short n' Sweet",
                    "artistName": "Sabrina Carpenter",
                    "editorialVideo": {
                      "motionDetailSquare": {"video": "https://mvod.itunes.apple.com/square.m3u8"},
                      "motionDetailTall": {"video": "https://mvod.itunes.apple.com/tall.m3u8"}
                    }
                  },
                  "relationships": {"tracks": {"data": []}}
                }
              ]
            }
        """.trimIndent()
        val alb = AlbumDetail.parse(albumJson)!!
        assertEquals("https://mvod.itunes.apple.com/square.m3u8", alb.motionSquareUrl)
        assertEquals("https://mvod.itunes.apple.com/tall.m3u8", alb.motionTallUrl)

        val artistJson = """
            {
              "data": [
                {
                  "id": "159260351",
                  "type": "artists",
                  "attributes": {
                    "name": "Taylor Swift",
                    "editorialArtwork": {
                      "musicContentColorLogoTrimmed": {
                        "url": "https://is1-ssl.mzstatic.com/logo/{w}x{h}{c}.{f}",
                        "width": 1600,
                        "height": 400
                      }
                    },
                    "editorialVideo": {
                      "motionArtistSquare1x1": {"video": "https://mvod.itunes.apple.com/artist_hero.m3u8"}
                    }
                  },
                  "views": {}
                }
              ]
            }
        """.trimIndent()
        val art = com.applemusic.android.data.model.ArtistDetail.parse(artistJson)!!
        assertEquals("https://is1-ssl.mzstatic.com/logo/1200x300bb.png", art.nameLogoUrl)
        assertEquals(4.0f, art.nameLogoAspectRatio!!, 0.01f)
        assertEquals("https://mvod.itunes.apple.com/artist_hero.m3u8", art.motionHeroUrl)
    }

    @Test
    fun `Feed snapshots round-trip cleanly and AudioSpatialCapabilities maps audio variants`() {
        val sample = listOf(
            com.applemusic.android.data.model.HomeSection(
                id = "sec_1",
                title = "Inicio rápido",
                hasChevron = true,
                style = HomeSectionStyle.SQUARE_CAROUSEL,
                items = listOf(
                    com.applemusic.android.data.model.HomeItem(
                        id = "123",
                        type = "albums",
                        title = "Álbum",
                        subtitle = "Artista",
                        artworkUrl = "https://example.com/a.jpg",
                        motionArtworkUrl = "https://mvod.itunes.apple.com/a.m3u8"
                    )
                )
            )
        )
        val encoded = kotlinx.serialization.json.Json.encodeToString(
            kotlinx.serialization.builtins.ListSerializer(
                com.applemusic.android.data.model.HomeSection.serializer()
            ),
            sample
        )
        val decoded = kotlinx.serialization.json.Json.decodeFromString(
            kotlinx.serialization.builtins.ListSerializer(
                com.applemusic.android.data.model.HomeSection.serializer()
            ),
            encoded
        )
        assertEquals(sample, decoded)
        assertEquals(
            "Dolby Atmos",
            com.applemusic.android.playback.AudioSpatialCapabilities.mapAudioVariantBadge(
                com.apple.android.music.playback.player.AudioConstants.TRACK_VARIANTS_DOLBY_ATMOS
            )
        )
        assertEquals(
            null,
            com.applemusic.android.playback.AudioSpatialCapabilities.mapAudioVariantBadge(
                com.apple.android.music.playback.player.AudioConstants.TRACK_VARIANTS_LOSSLESS
            )
        )
    }

    @Test
    fun `Pre-release album marks unavailable tracks while keeping released singles playable`() {
        val futureAlbumJson = """
            {
              "data": [{
                "id": "999999",
                "type": "albums",
                "attributes": {
                  "name": "Próximo Álbum",
                  "artistName": "Artista",
                  "releaseDate": "2099-12-31",
                  "isPrerelease": true,
                  "artwork": {"url": "https://img/{w}x{h}bb.{f}"}
                },
                "relationships": {
                  "tracks": {
                    "data": [
                      {
                        "id": "1001",
                        "type": "songs",
                        "attributes": {
                          "name": "Sencillo Disponible",
                          "artistName": "Artista",
                          "trackNumber": 1,
                          "discNumber": 1,
                          "durationInMillis": 205000,
                          "playParams": {"id": "1001", "kind": "song"}
                        }
                      },
                      {
                        "id": "1002",
                        "type": "songs",
                        "attributes": {
                          "name": "Pista Bloqueada",
                          "artistName": "Artista",
                          "trackNumber": 2,
                          "discNumber": 1,
                          "durationInMillis": 0
                        }
                      }
                    ]
                  }
                }
              }]
            }
        """.trimIndent()
        val album = com.applemusic.android.data.model.AlbumDetail.parse(futureAlbumJson)!!
        assertEquals(true, album.isPreRelease)
        assertEquals(true, album.releaseEpochMillis != null && album.releaseEpochMillis!! > System.currentTimeMillis())
        assertEquals(2, album.tracks.size)
        assertEquals(true, album.tracks[0].isAvailable)
        assertEquals(false, album.tracks[1].isAvailable)
    }

    @Test
    fun `Parses personal social profile photo and extracts favorite artist catalogId from ra_a station`() {
        val profileJson = """
            {
              "data": [{"id": "social-1", "type": "personal-social-profiles"}],
              "resources": {
                "personal-social-profiles": {
                  "me": {
                    "id": "social-1",
                    "type": "personal-social-profiles",
                    "attributes": {
                      "name": "Jezra",
                      "handle": "jezra99",
                      "artwork": {"url": "https://is1-ssl.mzstatic.com/avatar/{w}x{h}cc.{f}"}
                    }
                  }
                }
              }
            }
        """.trimIndent()
        val me = com.applemusic.android.data.model.SocialProfileDetail.parseMyAccountProfile(profileJson)!!
        assertEquals("Jezra", me.name)
        assertEquals("jezra99", me.handle)
        assertEquals("https://is1-ssl.mzstatic.com/avatar/320x320cc.jpg", me.avatarUrl)

        val homeJson = """
            {
              "data": [{"id": "18-fav", "type": "personal-recommendation"}],
              "resources": {
                "personal-recommendation": {
                  "18-fav": {
                    "id": "18-fav",
                    "type": "personal-recommendation",
                    "attributes": {
                      "title": {"stringForDisplay": "Artistas favoritos"},
                      "display": {"kind": "MusicCircleCoverShelf"}
                    },
                    "relationships": {
                      "contents": {"data": [{"id": "ra.a-1854923384", "type": "stations"}]}
                    }
                  }
                },
                "stations": {
                  "ra.a-1854923384": {
                    "id": "ra.a-1854923384",
                    "type": "stations",
                    "attributes": {"name": "LENCHO", "artwork": {"url": "https://art/{w}x{h}cc.{f}"}}
                  }
                }
              }
            }
        """.trimIndent()
        val page = WebHomeParser.parsePage(homeJson)
        val item = page.sections.single().items.single()
        assertEquals("ra.a-1854923384", item.id)
        assertEquals("1854923384", item.catalogId)
    }
}
