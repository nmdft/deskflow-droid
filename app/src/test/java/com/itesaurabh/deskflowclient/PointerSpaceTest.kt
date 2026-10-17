// Modified by nmdft on 2026-10-17; see NOTICE.md for fork changes and license.
package com.itesaurabh.deskflowclient

import org.junit.Assert.*
import org.junit.Test

class PointerSpaceTest {
    @Test fun landscapeMountRightMovesDownInPortraitDisplay() {
        val p=PointerSpace(1080,2400,0,1)
        assertEquals(2400,p.width); assertEquals(1080,p.height)
        assertEquals(879f to 100f,p.toDisplay(100f,200f))
        assertEquals(879f to 110f,p.toDisplay(110f,200f))
        assertEquals(869f to 100f,p.toDisplay(100f,210f))
    }
    @Test fun logicalDimensionsRemainStableAcrossAllAppRotations() {
        for (rotation in 0..3) {
            val w=if(rotation%2==0)1080 else 2400
            val h=if(rotation%2==0)2400 else 1080
            for (reverse in listOf(false,true)) {
                val p=PointerSpace(w,h,rotation,1,reverse)
                assertEquals(2400,p.width);assertEquals(1080,p.height)
                for (x in listOf(0f,2399f)) for(y in listOf(0f,1079f)) {
                    val point=p.toDisplay(x,y)
                    assertTrue(point.first in 0f..(w-1f));assertTrue(point.second in 0f..(h-1f))
                }
            }
        }
    }
    @Test fun followingDisplayIsIdentity() {
        for(r in 0..3) {
            val p=PointerSpace(if(r%2==0)1080 else 2400,if(r%2==0)2400 else 1080,r,-1)
            assertEquals(20f to 30f,p.toDisplay(20f,30f))
        }
    }
    @Test fun portraitMountAndReverseLandscapeMapCorrectly() {
        assertEquals(1080,PointerSpace(2400,1080,1,0).width)
        assertEquals(30f to 1059f,PointerSpace(2400,1080,1,0).toDisplay(20f,30f))
        assertEquals(200f to 2299f,PointerSpace(1080,2400,0,1,true).toDisplay(100f,200f))
    }
}
