package io.github.vferries.encarte.core.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Test

class EncarteShapesTest {
    @Test
    fun textFieldsMenusAndSnackbarsGetSixDp() {
        assertEquals(RoundedCornerShape(6.dp), EncarteShapes.extraSmall)
    }

    @Test
    fun buttonsTilesAndFabGetEightDp() {
        assertEquals(RoundedCornerShape(8.dp), EncarteShapes.small)
        assertEquals(RoundedCornerShape(8.dp), EncarteShapes.medium)
        assertEquals(RoundedCornerShape(8.dp), EncarteShapes.large)
    }

    @Test
    fun dialogsKeepTheMaterialDefault() {
        assertEquals(Shapes().extraLarge, EncarteShapes.extraLarge)
    }
}
