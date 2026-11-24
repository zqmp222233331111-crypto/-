package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.Build
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.model.AppLanguage
import com.example.ui.theme.AccentVioletBright
import com.example.ui.theme.AccentVioletPrimary
import com.example.ui.theme.LocalShailaColors

object ShailaRoutes {
    const val ONBOARDING = "onboarding"
    const val HOME = "home"
    const val GALLERY = "gallery"
    const val TOOLS = "tools"
    const val SETTINGS = "settings"
    const val MEMORY = "memory"
    const val IMAGE_REGION_DEBUG = "image_region_debug"
}

data class BottomTabItem(
    val route: String,
    val labelBn: String,
    val labelEn: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
    val testTag: String
)

val shailaBottomTabs = listOf(
    BottomTabItem(
        route = ShailaRoutes.HOME,
        labelBn = "Home",
        labelEn = "Home",
        selectedIcon = Icons.Filled.Home,
        unselectedIcon = Icons.Outlined.Home,
        testTag = "nav_home"
    ),
    BottomTabItem(
        route = ShailaRoutes.GALLERY,
        labelBn = "Gallery",
        labelEn = "Gallery",
        selectedIcon = Icons.Filled.PhotoLibrary,
        unselectedIcon = Icons.Outlined.PhotoLibrary,
        testTag = "nav_gallery"
    ),
    BottomTabItem(
        route = ShailaRoutes.TOOLS,
        labelBn = "Tools",
        labelEn = "Tools",
        selectedIcon = Icons.Filled.Build,
        unselectedIcon = Icons.Outlined.Build,
        testTag = "nav_tools"
    ),
    BottomTabItem(
        route = ShailaRoutes.SETTINGS,
        labelBn = "Settings",
        labelEn = "Settings",
        selectedIcon = Icons.Filled.Settings,
        unselectedIcon = Icons.Outlined.Settings,
        testTag = "nav_settings"
    )
)

@Composable
fun ShailaBottomBar(
    currentRoute: String?,
    appLanguage: AppLanguage,
    onNavigate: (String) -> Unit
) {
    val ext = LocalShailaColors.current
    NavigationBar(
        containerColor = ext.glassFill.copy(alpha = 0.94f),
        tonalElevation = 0.dp,
        windowInsets = WindowInsets.navigationBars
    ) {
        shailaBottomTabs.forEach { tab ->
            val selected = currentRoute == tab.route
            val label = if (appLanguage == AppLanguage.BENGALI) tab.labelBn else tab.labelEn
            NavigationBarItem(
                selected = selected,
                onClick = { onNavigate(tab.route) },
                modifier = Modifier.testTag(tab.testTag),
                icon = {
                    if (selected) {
                        Box(
                            modifier = Modifier
                                .shadow(
                                    elevation = 10.dp,
                                    shape = RoundedCornerShape(14.dp),
                                    ambientColor = AccentVioletBright,
                                    spotColor = AccentVioletBright
                                )
                                .clip(RoundedCornerShape(14.dp))
                                .background(
                                    Brush.horizontalGradient(
                                        listOf(
                                            AccentVioletPrimary.copy(alpha = 0.35f),
                                            AccentVioletBright.copy(alpha = 0.25f)
                                        )
                                    )
                                )
                                .padding(horizontal = 14.dp, vertical = 5.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = tab.selectedIcon,
                                contentDescription = label,
                                tint = AccentVioletBright
                            )
                        }
                    } else {
                        Icon(
                            imageVector = tab.unselectedIcon,
                            contentDescription = label
                        )
                    }
                },
                label = {
                    Text(
                        text = label,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                        color = if (selected) AccentVioletBright else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = AccentVioletBright,
                    selectedTextColor = AccentVioletBright,
                    indicatorColor = Color.Transparent,
                    unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
                )
            )
        }
    }
}
