package dev.bikram.remember.googletasks

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

/**
 * Pure unit coverage for the parts of the Google Tasks import pipeline that don't depend on
 * Android (JSON wire-format parsing). Behavioural coverage of the importer itself is exercised
 * via instrumented tests / manual QA because [GoogleTasksImporter] writes through Room.
 */
class GoogleTasksParsingTest {
    private val json =
        Json {
            ignoreUnknownKeys = true
            isLenient = true
            coerceInputValues = true
        }

    @Test
    fun `parses task lists response with multiple lists`() {
        val payload =
            """
            {
              "kind": "tasks#taskLists",
              "items": [
                { "id": "abc", "title": "My Tasks", "updated": "2026-04-25T12:00:00Z" },
                { "id": "def", "title": "Groceries" }
              ]
            }
            """.trimIndent()
        val parsed = json.decodeFromString(GoogleTaskListsResponse.serializer(), payload)
        assertEquals(2, parsed.items.size)
        assertEquals("My Tasks", parsed.items[0].title)
        assertEquals("def", parsed.items[1].id)
        assertNull(parsed.nextPageToken)
    }

    @Test
    fun `parses tasks response and tolerates unknown fields`() {
        val payload =
            """
            {
              "kind": "tasks#tasks",
              "items": [
                {
                  "id": "t1",
                  "title": "Buy milk",
                  "notes": "2 percent\nlactose free",
                  "status": "needsAction",
                  "due": "2026-05-01T00:00:00.000Z",
                  "position": "00000000000000000001",
                  "etag": "ignore-me",
                  "selfLink": "ignore-me-too"
                },
                {
                  "id": "t2",
                  "title": "Renew passport",
                  "status": "completed",
                  "completed": "2026-04-20T15:42:11.000Z",
                  "parent": "t1"
                },
                {
                  "id": "t3",
                  "deleted": true
                }
              ],
              "nextPageToken": "next123"
            }
            """.trimIndent()
        val parsed = json.decodeFromString(GoogleTasksResponse.serializer(), payload)
        assertEquals(3, parsed.items.size)
        assertEquals("next123", parsed.nextPageToken)

        val first = parsed.items[0]
        assertEquals("Buy milk", first.title)
        assertEquals("needsAction", first.status)
        assertEquals("2026-05-01T00:00:00.000Z", first.due)
        assertTrue(first.notes!!.contains("lactose free"))

        val second = parsed.items[1]
        assertEquals(GoogleTaskStatus.COMPLETED, second.status)
        assertEquals("2026-04-20T15:42:11.000Z", second.completed)
        assertEquals("t1", second.parent)

        val third = parsed.items[2]
        assertEquals(true, third.deleted)
        assertNull(third.title)
    }

    @Test
    fun `empty items list is allowed`() {
        val payload = """{ "kind": "tasks#tasks", "items": [] }"""
        val parsed = json.decodeFromString(GoogleTasksResponse.serializer(), payload)
        assertTrue(parsed.items.isEmpty())
        assertNull(parsed.nextPageToken)
    }

    @Test
    fun `missing items key falls back to empty list`() {
        val payload = """{ "kind": "tasks#tasks" }"""
        val parsed = json.decodeFromString(GoogleTasksResponse.serializer(), payload)
        assertTrue(parsed.items.isEmpty())
    }

    @Test
    fun `parses takeout task lists with nested tasks`() {
        val payload =
            """
            {
              "items": [
                {
                  "id": "list-1",
                  "title": "Errands",
                  "tasks": [
                    {
                      "id": "parent-1",
                      "title": "Buy groceries",
                      "notes": "Milk and rice",
                      "status": "needsAction",
                      "due": "2026-05-01T00:00:00.000Z",
                      "position": "0001"
                    },
                    {
                      "id": "child-1",
                      "title": "Check coupons",
                      "parent": "parent-1",
                      "completed": "2026-04-20T15:42:11.000Z"
                    }
                  ]
                }
              ]
            }
            """.trimIndent()

        val parsed = GoogleTasksTakeoutParser().parse(payload)

        assertEquals(1, parsed.taskLists.size)
        assertEquals("Errands", parsed.taskLists.first().title)
        assertEquals(2, parsed.tasks.size)
        assertEquals("takeout:list-1:parent-1", parsed.tasks[0].task.id)
        assertEquals("takeout:list-1:parent-1", parsed.tasks[1].task.parent)
        assertEquals(GoogleTaskStatus.COMPLETED, parsed.tasks[1].task.status)
    }

    @Test
    fun `parses takeout file with a direct tasks array`() {
        val payload =
            """
            {
              "tasks": [
                {
                  "id": "task-1",
                  "title": "Renew passport",
                  "description": "Bring photo"
                }
              ]
            }
            """.trimIndent()

        val parsed = GoogleTasksTakeoutParser().parse(payload)

        assertEquals(1, parsed.taskLists.size)
        assertEquals("Google Tasks", parsed.taskLists.first().title)
        assertEquals(
            "Renew passport",
            parsed.tasks
                .first()
                .task.title,
        )
        assertEquals(
            "Bring photo",
            parsed.tasks
                .first()
                .task.notes,
        )
    }

    @Test
    fun `collapses takeout recurring instances by recurrence id`() {
        val payload =
            """
            {
              "items": [
                {
                  "id": "list-1",
                  "title": "Personal",
                  "recurrences": [
                    { "id": "rec-weekly", "title": "Water plants" }
                  ],
                  "tasks": [
                    {
                      "id": "old",
                      "title": "Water plants",
                      "status": "completed",
                      "completed": "2026-04-25T10:00:00Z",
                      "scheduled_time": [{ "start": "2026-04-25T10:00:00Z" }],
                      "task_recurrence_id": "rec-weekly"
                    },
                    {
                      "id": "next",
                      "title": "Water plants",
                      "status": "needsAction",
                      "scheduled_time": [{ "current": true, "start": "2026-05-02T10:00:00Z" }],
                      "task_recurrence_id": "rec-weekly"
                    },
                    {
                      "id": "later",
                      "title": "Water plants",
                      "status": "needsAction",
                      "scheduled_time": [{ "start": "2026-05-09T10:00:00Z" }],
                      "task_recurrence_id": "rec-weekly"
                    }
                  ]
                }
              ]
            }
            """.trimIndent()

        val parsed = GoogleTasksTakeoutParser(now = { Instant.parse("2026-05-01T00:00:00Z") }).parse(payload)

        assertEquals(1, parsed.tasks.size)
        assertEquals(
            "takeout-series:list-1:rec-weekly",
            parsed.tasks
                .first()
                .task.id,
        )
        assertEquals(
            "2026-05-02T10:00:00Z",
            parsed.tasks
                .first()
                .task.due,
        )
        assertEquals(3, parsed.stats.originalTaskCount)
        assertEquals(1, parsed.stats.importedTaskCount)
        assertEquals(2, parsed.stats.collapsedInstanceCount)
        assertEquals(1, parsed.stats.recurringSeriesCount)
    }

    @Test
    fun `collapses completed historical recurrence representatives with same content`() {
        val payload =
            """
            {
              "items": [
                {
                  "id": "list-1",
                  "title": "My Tasks",
                  "recurrences": [
                    { "id": "rec-weekly", "title": "Kitchen Cleaning" },
                    { "id": "rec-three-week", "title": "Kitchen Cleaning" }
                  ],
                  "tasks": [
                    {
                      "id": "old-weekly",
                      "title": "Kitchen Cleaning",
                      "status": "completed",
                      "completed": "2023-02-26T18:44:57Z",
                      "scheduled_time": [{ "current": true, "start": "2023-04-09T17:00:00Z" }],
                      "task_recurrence_id": "rec-weekly"
                    },
                    {
                      "id": "older-weekly",
                      "title": "Kitchen Cleaning",
                      "status": "completed",
                      "completed": "2023-03-19T04:44:25Z",
                      "scheduled_time": [{ "current": true, "start": "2023-03-19T17:00:00Z" }],
                      "task_recurrence_id": "rec-weekly"
                    },
                    {
                      "id": "old-three-week",
                      "title": "Kitchen Cleaning",
                      "status": "completed",
                      "completed": "2023-06-25T18:15:04Z",
                      "scheduled_time": [{ "current": true, "start": "2023-06-25T17:00:00Z" }],
                      "task_recurrence_id": "rec-three-week"
                    }
                  ]
                }
              ]
            }
            """.trimIndent()

        val parsed = GoogleTasksTakeoutParser(now = { Instant.parse("2026-05-01T00:00:00Z") }).parse(payload)

        assertEquals(1, parsed.tasks.size)
        assertEquals(
            "Kitchen Cleaning",
            parsed.tasks
                .first()
                .task.title,
        )
        assertEquals(2, parsed.stats.collapsedInstanceCount)
    }

    @Test
    fun `skips empty takeout task shells`() {
        val payload =
            """
            {
              "items": [
                {
                  "id": "list-1",
                  "title": "My Tasks",
                  "tasks": [
                    {
                      "id": "empty-1",
                      "title": "",
                      "status": "needsAction"
                    },
                    {
                      "id": "real-1",
                      "title": "Real task",
                      "status": "needsAction"
                    }
                  ]
                }
              ]
            }
            """.trimIndent()

        val parsed = GoogleTasksTakeoutParser().parse(payload)

        assertEquals(1, parsed.tasks.size)
        assertEquals(
            "Real task",
            parsed.tasks
                .first()
                .task.title,
        )
        assertEquals(2, parsed.stats.originalTaskCount)
        assertEquals(1, parsed.stats.importedTaskCount)
    }

    @Test
    fun `fallback recurrence collapse keeps different notes separate`() {
        val payload =
            """
            {
              "items": [
                {
                  "id": "list-1",
                  "title": "Personal",
                  "tasks": [
                    {
                      "id": "first",
                      "title": "Pay bill",
                      "notes": "Water",
                      "due": "2026-05-01T00:00:00Z"
                    },
                    {
                      "id": "second",
                      "title": "Pay bill",
                      "notes": "Electric",
                      "due": "2026-06-01T00:00:00Z"
                    }
                  ]
                }
              ]
            }
            """.trimIndent()

        val parsed = GoogleTasksTakeoutParser().parse(payload)

        assertEquals(2, parsed.tasks.size)
        assertEquals(0, parsed.stats.collapsedInstanceCount)
        assertEquals(0, parsed.stats.recurringSeriesCount)
    }

    @Test
    fun `fallback collapse handles repeated same content without recurrence metadata`() {
        val payload =
            """
            {
              "items": [
                {
                  "id": "list-1",
                  "title": "Personal",
                  "tasks": [
                    {
                      "id": "first",
                      "title": "Pay rent",
                      "notes": "Apartment",
                      "due": "2026-04-01T00:00:00Z",
                      "completed": "2026-04-01T12:00:00Z"
                    },
                    {
                      "id": "second",
                      "title": "Pay rent",
                      "notes": "Apartment",
                      "due": "2026-05-01T00:00:00Z"
                    }
                  ]
                }
              ]
            }
            """.trimIndent()

        val parsed = GoogleTasksTakeoutParser(now = { Instant.parse("2026-04-15T00:00:00Z") }).parse(payload)

        assertEquals(1, parsed.tasks.size)
        assertEquals(
            "2026-05-01T00:00:00Z",
            parsed.tasks
                .first()
                .task.due,
        )
        assertEquals(1, parsed.stats.collapsedInstanceCount)
        assertEquals(1, parsed.stats.recurringSeriesCount)
    }

    @Test
    fun `same takeout title in different lists stays separate`() {
        val payload =
            """
            {
              "items": [
                {
                  "id": "home",
                  "title": "Home",
                  "tasks": [
                    {
                      "id": "home-task",
                      "title": "Review",
                      "due": "2026-05-01T00:00:00Z"
                    }
                  ]
                },
                {
                  "id": "work",
                  "title": "Work",
                  "tasks": [
                    {
                      "id": "work-task",
                      "title": "Review",
                      "due": "2026-05-08T00:00:00Z"
                    }
                  ]
                }
              ]
            }
            """.trimIndent()

        val parsed = GoogleTasksTakeoutParser().parse(payload)

        assertEquals(2, parsed.tasks.size)
        assertEquals(setOf("takeout:home:home-task", "takeout:work:work-task"), parsed.tasks.map { it.task.id }.toSet())
        assertEquals(0, parsed.stats.collapsedInstanceCount)
    }
}
