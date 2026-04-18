package dev.bikram.remember.ui.edit

import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Assignment
import androidx.compose.material.icons.automirrored.filled.Notes
import androidx.compose.material.icons.automirrored.filled.StickyNote2
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.AttachMoney
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Cake
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.ChildCare
import androidx.compose.material.icons.filled.Checkroom
import androidx.compose.material.icons.filled.Computer
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.Event
import androidx.compose.material.icons.filled.Face
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.Flight
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Hotel
import androidx.compose.material.icons.filled.LocalBar
import androidx.compose.material.icons.filled.LocalCafe
import androidx.compose.material.icons.filled.LocalFlorist
import androidx.compose.material.icons.filled.LocalGroceryStore
import androidx.compose.material.icons.filled.LocalLaundryService
import androidx.compose.material.icons.filled.LocalPizza
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.MedicalServices
import androidx.compose.material.icons.filled.Medication
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Park
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Pets
import androidx.compose.material.icons.filled.PhoneIphone
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.ShoppingBag
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material.icons.filled.Spa
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Train
import androidx.compose.material.icons.filled.Umbrella
import androidx.compose.material.icons.filled.Water
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material.icons.filled.Work
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import dev.bikram.remember.R

data class IconChoice(
    val key: String,
    @param:StringRes val labelRes: Int,
    val vector: ImageVector,
)

data class IconCategory(
    @param:StringRes val nameRes: Int,
    val icons: List<IconChoice>,
)

/** Stored in note `iconKey`; payload is one or more emoji code units (keyboard / picker). */
const val ICON_EMOJI_PREFIX: String = "emoji:"

val iconCatalog: List<IconCategory> = listOf(
    IconCategory(
        R.string.icon_cat_general,
        listOf(
            IconChoice("notes", R.string.icon_name_notes, Icons.AutoMirrored.Filled.Notes),
            IconChoice("stickyNote", R.string.icon_name_sticky_note, Icons.AutoMirrored.Filled.StickyNote2),
            IconChoice("editNote", R.string.icon_name_edit_note, Icons.Filled.EditNote),
            IconChoice("star", R.string.icon_name_star, Icons.Filled.Star),
            IconChoice("favorite", R.string.icon_name_favorite, Icons.Filled.Favorite),
            IconChoice("bookmark", R.string.icon_name_bookmark, Icons.Filled.Bookmark),
        ),
    ),
    IconCategory(
        R.string.icon_cat_people,
        listOf(
            IconChoice("person", R.string.icon_name_person, Icons.Filled.Person),
            IconChoice("group", R.string.icon_name_group, Icons.Filled.Group),
            IconChoice("face", R.string.icon_name_face, Icons.Filled.Face),
            IconChoice("childCare", R.string.icon_name_child, Icons.Filled.ChildCare),
            IconChoice("pets", R.string.icon_name_pets, Icons.Filled.Pets),
        ),
    ),
    IconCategory(
        R.string.icon_cat_places,
        listOf(
            IconChoice("home", R.string.icon_name_home, Icons.Filled.Home),
            IconChoice("work", R.string.icon_name_work, Icons.Filled.Work),
            IconChoice("school", R.string.icon_name_school, Icons.Filled.School),
            IconChoice("location", R.string.icon_name_location, Icons.Filled.LocationOn),
            IconChoice("map", R.string.icon_name_map, Icons.Filled.Map),
        ),
    ),
    IconCategory(
        R.string.icon_cat_food,
        listOf(
            IconChoice("restaurant", R.string.icon_name_restaurant, Icons.Filled.Restaurant),
            IconChoice("cafe", R.string.icon_name_cafe, Icons.Filled.LocalCafe),
            IconChoice("bar", R.string.icon_name_bar, Icons.Filled.LocalBar),
            IconChoice("pizza", R.string.icon_name_pizza, Icons.Filled.LocalPizza),
            IconChoice("cake", R.string.icon_name_cake, Icons.Filled.Cake),
        ),
    ),
    IconCategory(
        R.string.icon_cat_travel,
        listOf(
            IconChoice("flight", R.string.icon_name_flight, Icons.Filled.Flight),
            IconChoice("car", R.string.icon_name_car, Icons.Filled.DirectionsCar),
            IconChoice("train", R.string.icon_name_train, Icons.Filled.Train),
            IconChoice("hotel", R.string.icon_name_hotel, Icons.Filled.Hotel),
            IconChoice("grocery", R.string.icon_name_grocery, Icons.Filled.LocalGroceryStore),
        ),
    ),
    IconCategory(
        R.string.icon_cat_work,
        listOf(
            IconChoice("event", R.string.icon_name_event, Icons.Filled.Event),
            IconChoice("calendar", R.string.icon_name_calendar, Icons.Filled.CalendarToday),
            IconChoice("time", R.string.icon_name_time, Icons.Filled.AccessTime),
            IconChoice("assignment", R.string.icon_name_assignment, Icons.AutoMirrored.Filled.Assignment),
            IconChoice("money", R.string.icon_name_money, Icons.Filled.AttachMoney),
        ),
    ),
    IconCategory(
        R.string.icon_cat_health,
        listOf(
            IconChoice("fitness", R.string.icon_name_fitness, Icons.Filled.FitnessCenter),
            IconChoice("medical", R.string.icon_name_medical, Icons.Filled.MedicalServices),
            IconChoice("sleep", R.string.icon_name_sleep, Icons.Filled.Bedtime),
            IconChoice("medication", R.string.icon_name_medication, Icons.Filled.Medication),
            IconChoice("spa", R.string.icon_name_spa, Icons.Filled.Spa),
        ),
    ),
    IconCategory(
        R.string.icon_cat_tech,
        listOf(
            IconChoice("computer", R.string.icon_name_computer, Icons.Filled.Computer),
            IconChoice("phone", R.string.icon_name_phone, Icons.Filled.PhoneIphone),
            IconChoice("headphones", R.string.icon_name_headphones, Icons.Filled.Headphones),
            IconChoice("music", R.string.icon_name_music, Icons.Filled.MusicNote),
            IconChoice("camera", R.string.icon_name_camera, Icons.Filled.CameraAlt),
        ),
    ),
    IconCategory(
        R.string.icon_cat_lifestyle,
        listOf(
            IconChoice("shoppingCart", R.string.icon_name_shopping_cart, Icons.Filled.ShoppingCart),
            IconChoice("shoppingBag", R.string.icon_name_shopping_bag, Icons.Filled.ShoppingBag),
            IconChoice("laundry", R.string.icon_name_laundry, Icons.Filled.LocalLaundryService),
            IconChoice("clothes", R.string.icon_name_clothes, Icons.Filled.Checkroom),
            IconChoice("games", R.string.icon_name_games, Icons.Filled.SportsEsports),
        ),
    ),
    IconCategory(
        R.string.icon_cat_nature,
        listOf(
            IconChoice("sun", R.string.icon_name_sun, Icons.Filled.WbSunny),
            IconChoice("rain", R.string.icon_name_rain, Icons.Filled.Umbrella),
            IconChoice("flower", R.string.icon_name_flower, Icons.Filled.LocalFlorist),
            IconChoice("park", R.string.icon_name_park, Icons.Filled.Park),
            IconChoice("water", R.string.icon_name_water, Icons.Filled.Water),
        ),
    ),
)

private val iconByKey: Map<String, IconChoice> =
    iconCatalog.flatMap { it.icons }.associateBy { it.key }

fun iconFor(key: String?): ImageVector? =
    key?.takeUnless { it.startsWith(ICON_EMOJI_PREFIX) }?.let { catalogKey -> iconByKey[catalogKey]?.vector }

fun iconEmojiPayload(iconKey: String?): String? =
    iconKey
        ?.takeIf { it.startsWith(ICON_EMOJI_PREFIX) }
        ?.removePrefix(ICON_EMOJI_PREFIX)
        ?.takeIf { it.isNotBlank() }

@Composable
fun iconLabelFor(key: String?): String? = when {
    key == null -> null
    key.startsWith(ICON_EMOJI_PREFIX) -> iconEmojiPayload(key)
    else -> iconByKey[key]?.let { choice -> stringResource(choice.labelRes) }
}
