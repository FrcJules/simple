package app.presence.launcher.photos

import org.junit.Assert.assertEquals
import org.junit.Test

class ContactPhotoStoreTest {
    @Test
    fun smallImagesAreNotSubsampled() = assertEquals(1, ContactPhotoStore.sampleSizeFor(800, 600))

    @Test
    fun largeImagesAreSubsampledByPowersOfTwoKeepingAtLeast512px() {
        assertEquals(4, ContactPhotoStore.sampleSizeFor(4000, 3000)) // 4000/4 = 1000 >= 512, /8 = 500 < 512
        assertEquals(8, ContactPhotoStore.sampleSizeFor(4096, 3072))
    }

    @Test
    fun exifOrientationMapsToRotation() {
        assertEquals(90, ContactPhotoStore.rotationDegrees(6))
        assertEquals(180, ContactPhotoStore.rotationDegrees(3))
        assertEquals(270, ContactPhotoStore.rotationDegrees(8))
        assertEquals(0, ContactPhotoStore.rotationDegrees(1))
    }
}
