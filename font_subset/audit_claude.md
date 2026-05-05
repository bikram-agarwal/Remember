1. setAlarmClock for HIGH-importance reminders (Small)
    
    ~~ReminderScheduler.kt:36 currently uses setExactAndAllowWhileIdle uniformly. Branching on note.importance == Importance.HIGH to use setAlarmClock gets exemption from Doze, no SCHEDULE_EXACT_ALARM permission needed, and the status-bar alarm icon (which is arguably desirable for a reminder app — visible "next reminder" indicator).~~
    
    ~~This is the one real reliability win on the table.~~

2. Search debouncing (Small)

    ~~HomeViewModel has distinctUntilChanged but no actual debounce. They're not the same — distinctUntilChanged filters duplicates, but every keystroke "a", "ab", "abc" is distinct and runs three FTS queries. A 150ms debounce collapses burst typing.~~

3. MainActivity : FragmentActivity → ComponentActivity (Small)
    
    ~~Cleanup only. Zero behavioral change. Useful only because there are no Fragments anywhere.~~

4. AMOLED auto-dark-by-clock (Small, feature)

    ~~ThemeMode has {SYSTEM, LIGHT, DARK, BLACK}. Adding a time-based mode (sunset/sunrise or fixed hours) is a small enhancement.~~

5. Use existing shape-morph machinery in more places (Medium)

    ~~The infra exists (Morph, ExpressivePolygonShape) but the audit's specific suggestions (card → expanded detail morph, FAB → multi-select action bar morph) need verification against actual UI flow before recommending. I shouldn't promise these without checking they fit.~~

6. ~~Templates (Medium, feature) — unchanged from original, no API verification needed.~~

7. ~~Cross-device sync (Large, feature, opt-in) — unchanged.~~

8. ~~A11y audit (Medium) — generic but always valuable.~~

### What I'd actually do first
If I were you: just setAlarmClock for HIGH importance + debouncing. That's it. Both are concrete, both are correct, both take an afternoon. Everything else is either feature work that needs a product decision (templates, sync, auto-dark) or polish that needs deeper inspection before recommending specifics.

Want me to start with one of those two?
