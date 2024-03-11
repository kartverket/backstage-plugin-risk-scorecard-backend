package no.kvros.encryption

import no.kvros.infra.connector.models.GCPAccessToken
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow
import org.junit.jupiter.api.assertThrows
import java.io.File

class SOPSTest {
    private val decryptedROS =
        File("src/test/kotlin/no/kvros/encryption/utils/ukryptert.ros.json").readText(Charsets.UTF_8)

    @Test
    fun `when ciphertext is not yaml an exception is thrown`() {
        val ciphertextThatIsJustAString =
            "ENC[AES256_GCM,data:dYo75pR4EvbtULEJ926/tm9qZns2n8LHkNg78GpYk41gZGd6awrZ3NVtWVFeu4ns,iv:pjcpGaqDfU0vy76PgF6ZdMOriXNfeANOoYyda8Mq9EA=,tag:Rcv+ZgI1n2fgKy8DSep4jQ==,type:str]"

        assertThrows<Exception> {
            SOPS.decrypt(
                ciphertext = ciphertextThatIsJustAString,
                gcpAccessToken = GCPAccessToken(""),
                System.getenv("SOPS_AGE_KEY")
            )
        }
    }

    @Test
    fun `when ciphertext is yaml then the json equivalent is returned`() {
        val ciphertextThatIsYaml =
            File("src/test/kotlin/no/kvros/encryption/utils/kryptert.ros_test.yaml").readText(Charsets.UTF_8)

        val actual =
            SOPS.decrypt(
                ciphertext = ciphertextThatIsYaml,
                gcpAccessToken = GCPAccessToken(""),
                System.getenv("SOPS_AGE_KEY")
            )

        assertThat(actual).isEqualTo(decryptedROS)
    }

    @Disabled
    @Test
    fun `test at sops fungerer med egen versjon`() {
        assertDoesNotThrow {
            SOPS.decrypt(
                ciphertext =
                "skjemaVersjon: ENC[AES256_GCM,data:QkZw9vG+w0wy,iv:k5AuVA4WuMhq8h7ipr4oJFyX7N83tU88EBVPipD2cvc=,tag:Xw8BCVH/Qh22GeRCdGlgMA==,type:str]\n" +
                        "tittel: ENC[AES256_GCM,data:/0jTxa0dpwEqkpmj7wPgoroO/hWRcTzr0FhxDQ==,iv:39PCh5ATh5OcuTt6HLNWz7Pi6tMZ4JxVD9oKgV+sxzc=,tag:QYH2fy3NjZZcnb5UQWyCjQ==,type:str]\n" +
                        "omfang: ENC[AES256_GCM,data:nQjREPYRRnSursdXhIJcJuQWeRpln5N8zZr49Krl3eLzZkf/5SxA1hjDnfhcWJU/aRPURFpBnFQJ7Hzptvk1nQHXzAGuGFjQ885Cb9tv/HZv33tNfTVxEwmfyBdlF/rTsQJ+tEdyt1+53ifewA==,iv:52nV5S9XTVnEeF0PsFEPGqAbYt8CEaBSsw3c7jXmS7E=,tag:s0KvFz3+eMlJkNXfLp2L6w==,type:str]\n" +
                        "scenarier:\n" +
                        "    - ID: ENC[AES256_GCM,data:nW3R,iv:gg23rbdt6AtqkqQuUPSiZ1z7jFSOCy1CT7GhMShQvn4=,tag:BtHTiJyTxFWuP5oxFZK/qw==,type:str]\n" +
                        "      tittel: ENC[AES256_GCM,data:7sCIepUM3+2XQDgMY6hCmIwHb1aAOnjuTnJ0oWjU,iv:T/wUWGQkU4lubXS6pwvVGZfqBAXRsNt3QJac46kYp4k=,tag:skzGZFTWWN95/khH9mezfg==,type:str]\n" +
                        "      beskrivelse: ENC[AES256_GCM,data:KCmxtzz6H9MgekijeYJPe/ngjSnjoiLoTEk/tuISDWzgTc0pYGJ1naEpehvlib1pCRchDOdMzHf9Ge8GS/YVhrFbyQ==,iv:SQIHf+k7eP5oOkr3JXfX6tMSAk6gJHb7FBpYtWQegH8=,tag:W6LkChYcM7zgkLVNqvUYqw==,type:str]\n" +
                        "      sistEndret: ENC[AES256_GCM,data:u1ZDiD3WgfrvXg==,iv:xiS/rgM9mHOVaqsyJFCz77Cg0hi4nCgrQkcK7kKr/sU=,tag:S4xeTQw09/lSdxL6s9W76w==,type:str]\n" +
                        "      trusselaktører:\n" +
                        "        - ENC[AES256_GCM,data:AA5ZQGrE8Z81lQcYkMs=,iv:Q0dM0MAEG+JjovcJcztVkipbxRtFr6ch4U+50ivOaKc=,tag:AdV/x8zfDCfYqia1EHPbmg==,type:str]\n" +
                        "      sårbarheter:\n" +
                        "        - ENC[AES256_GCM,data:4Fbsj5LNBFG1Bi4JATzn/T2RHpI=,iv:TCnouEZXfvVnGI06r9kuSTQfaaSAmsv1o/0HaBaooHQ=,tag:uyyzv7gScs3Lik8M5A6pGA==,type:str]\n" +
                        "      risiko:\n" +
                        "        oppsummering: ENC[AES256_GCM,data:T4BOeu4LpfoF4Go247+f7u9NT3pMj/NA3rhXobI83Mg+rCWaiW+A6TAq4Ttn9SvvJo9DZVUcE1lIsYHJl1S3oQuqSah32kshL0UKkZ3dT/810HsrKF/nyeaKj/bAiHAaCXcaQ64c6HfO2HvY7HexOYG8pEOnadej6VPn40RkSdhEXoXv4KBTloz82o7KRQpks1/cKu0B3gdYjS4O79lo2dgwpngACMAx77T1B9uiWOWc7SvHXISC1VfF/ZATZnxYvEtLdpQibaiVmHHB02GUJFDZ4keWA17zonKjZgLHuXrGBeOyYztPwayOQqz0d704ee6UV5jICE4rm3ANzN1vlKWiFXdgj4ujMTxIdBZrYDFGXritQxeSkM4/8dj+EpU1IXYEejIikiHKHWhWEvk8q1JhRYEyTYxuCA==,iv:usaxp4Cbcn6yT34refp1IfnXUPjD6xs9nvHmvXVL9NQ=,tag:HuTAOAmc4cWfLBiHSWOgZw==,type:str]\n" +
                        "        konsekvens: ENC[AES256_GCM,data:eHuPFoTpzIw=,iv:nnbTjNkXmp53lw/CWZobIBB65Bs2x8an3lp8gvtkrEY=,tag:AmeM3m4yR/SP3VsEi5xE8A==,type:int]\n" +
                        "        sannsynlighet: ENC[AES256_GCM,data:ug2v,iv:tMLKS4WMgdOlgD0FGQ2N6ZBwRtsBWBoDUkV9Er+qwQI=,tag:7aJIDmIy+DeA/5/JP1Z3YQ==,type:float]\n" +
                        "      tiltak:\n" +
                        "        - ID: ENC[AES256_GCM,data:tqYs,iv:xQ2TKxE2N0GIy0aMoRAs280tTaB3+v/SFUGWt5RhA0Q=,tag:nuh3KP3KKnP434O5s/uMMw==,type:str]\n" +
                        "          beskrivelse: ENC[AES256_GCM,data:MK8KGTpYlg9DVnfiHEaxHfV1+y2N6JkjRNSrIzU6hma9pTEDw4dddHoPgk9n+20MF2ERfUZ0Q9NSmMLxGQcOZ9OxdOwdF91+RW17WrJ2DwS5REL/lZgpsr4w7w==,iv:nkOJXeDi/qqx26qCYYK/Cy4lJpyNz3P8vquJRRrJl5g=,tag:x46QsCYhSWxohjtuRWNS3Q==,type:str]\n" +
                        "          tiltakseier: ENC[AES256_GCM,data:kBkn39EmcXvYDw==,iv:/xWVudBAJq77J1uZmwf+XRdDCUGIYgBVJ7sDrn6Upz8=,tag:qk4jlJUMYDzZI+E7YKlFKw==,type:str]\n" +
                        "          status: ENC[AES256_GCM,data:pgjFEpAAFA==,iv:5+37Iz0vvzTeNYESjlC5tSzTy+OmYYp1YRyvzhJJK50=,tag:q8lQN+TdjYt31iz7beVVOw==,type:str]\n" +
                        "          frist: ENC[AES256_GCM,data:pIng3cWMJT2bFA==,iv:Utp6HkPQPt2j/rAAbp530XOgzOm4bgDtBMsx7BxolzY=,tag:pxz1id/FLauFLuXLYFEqrQ==,type:str]\n" +
                        "      restrisiko:\n" +
                        "        konsekvens: ENC[AES256_GCM,data:L+oD7d5jXJ4=,iv:gX+YP+kBvsfVLVM6eySLThzHWn0Z278RadmTRiExBlA=,tag:Ic1tnKB0Yw9ShXQ46qxNhQ==,type:int]\n" +
                        "        sannsynlighet: ENC[AES256_GCM,data:DmHuPw==,iv:WayHJMzWGKATwI4u1a+MJc1tunYWQFQL48sdqWRpziI=,tag:ys6bldRoPU/S6hA/PLD5cA==,type:float]\n" +
                        "    - ID: ENC[AES256_GCM,data:iOme,iv:dYzyU4rj4uVOn2EWyHyz6dqq2+PN2XBg8Hqa9AAcj5M=,tag:3OeI+M8vZaH8XkbRdfLc4g==,type:str]\n" +
                        "      tittel: ENC[AES256_GCM,data:+RnpW+MeGd9+IboTyp4MrsmebunxiMUEYoneMOIXQg3w7Rv3md+/,iv:ktfb58RnQP8bBGfkG7Wdwijo6gFBxrUJdCXku+d1lv8=,tag:ZwMEVRP7wHJp8/DwHXQA7w==,type:str]\n" +
                        "      beskrivelse: ENC[AES256_GCM,data:GgMx2NThhcZNbRuQ3d2hlrjCpj09si2HH8h87sgodhx6lHnb5RSlX4D0Bw6s6VxgiVUl8cX75SOjYTzT7l569fpyjfMVi51fEj8SvLsd,iv:Wdi4S1snBlXVzem1v7AZ3rfGFpcA9Sdhq1KiriNVJxo=,tag:LC/MdDyCN73NOYs6O9NXyw==,type:str]\n" +
                        "      sistEndret: ENC[AES256_GCM,data:u1ZDiD3WgfrvXg==,iv:xiS/rgM9mHOVaqsyJFCz77Cg0hi4nCgrQkcK7kKr/sU=,tag:S4xeTQw09/lSdxL6s9W76w==,type:str]\n" +
                        "      trusselaktører:\n" +
                        "        - ENC[AES256_GCM,data:AA5ZQGrE8Z81lQcYkMs=,iv:Q0dM0MAEG+JjovcJcztVkipbxRtFr6ch4U+50ivOaKc=,tag:AdV/x8zfDCfYqia1EHPbmg==,type:str]\n" +
                        "        - ENC[AES256_GCM,data:Bmg18LfjWqA=,iv:aOteaifZmPd0XngGvCS2v6pdw7xEXyh6FmHlYO39d84=,tag:iHs1Y5A1hWmGkmsRrWuedQ==,type:str]\n" +
                        "        - ENC[AES256_GCM,data:5WnvyJ++tKx22Z0=,iv:BFlHyKRc37vVW9gAA/vVotR+LFtdJbdR8qrlFaChrHc=,tag:2/bUPOhmrvHWemhQb2ZxiA==,type:str]\n" +
                        "        - ENC[AES256_GCM,data:adwa29aaOJD2g6/OvyQ=,iv:AN0p+eMtU6wmSxvEjqwIdXkmQ3037ok8G4mpvNqzhjU=,tag:5ZoTbNa49KXiEOeDyBeSkw==,type:str]\n" +
                        "      sårbarheter:\n" +
                        "        - ENC[AES256_GCM,data:YPQDUXCmjZ6WdyIT3VIxJC8O,iv:qhDrDXiCag/twtBAgNlkZMsSwnZbF1tSIHNHpqwLw8Y=,tag:TkVVccSHFiejYGPT4wqIOg==,type:str]\n" +
                        "      risiko:\n" +
                        "        oppsummering: ENC[AES256_GCM,data:NrN8yuyOhhaIaZ5s47rc3bI8Pj5ZYuK0ZHeKI/nbaTwQ4ZyFFvulxpQozwzyvdIf6Hn0RwJqPjALUo2tjVIjrKNgoY6Ib3oOpUaCSpSC+UVTsbDHxGrheGWhOTFnLeW9jbW2lA3LCJmgWafd3nLFYTDdVdDcs4+j5QnIpmVBsILL/oBMZYpPJLQu,iv:U5iWIV/MtP3ABFafoeaNYOv7OUPJU4uIacW8IvQHheU=,tag:O5khqnvEnmAf+0qbKPBbNg==,type:str]\n" +
                        "        sannsynlighet: ENC[AES256_GCM,data:ug2v,iv:tMLKS4WMgdOlgD0FGQ2N6ZBwRtsBWBoDUkV9Er+qwQI=,tag:7aJIDmIy+DeA/5/JP1Z3YQ==,type:float]\n" +
                        "        konsekvens: ENC[AES256_GCM,data:6Q1bzOk=,iv:w3RHNkgNlpEpdrQBbkVjrtQbeM4tvy08dPtBxROnImg=,tag:mQTXo3o/QdVF07PZwM3mlg==,type:int]\n" +
                        "      tiltak:\n" +
                        "        - ID: ENC[AES256_GCM,data:2XA3,iv:spfW3nRlTCw4leJzYBNWC92cYEy11FFdBP9RZgb3hBw=,tag:RHtoJPgBV8PRmvLgV1Sasg==,type:str]\n" +
                        "          beskrivelse: ENC[AES256_GCM,data:8rvx9x5bZHlWmHLaqw9BwStDQiBH1bErSdP5M0iVas3Ay29EzWD4uJ1eD6GKRgt5+bQNWvjlCdNwEZo=,iv:SgaFuxSzlSnUhrBEmTY/HNLItQ1GaA1oP1MMCPj6z+E=,tag:OG+yBo3MHRpyrq16byqa4Q==,type:str]\n" +
                        "          tiltakseier: ENC[AES256_GCM,data:Hplu+xaOjtC4,iv:Gm1+QLXkIVHjHuqiUljrDmvWXQ0NORmDdTwFLBwidpA=,tag:swCXd05fQYY5RvNfPvbLRA==,type:str]\n" +
                        "          frist: ENC[AES256_GCM,data:pIng3cWMJT2bFA==,iv:Utp6HkPQPt2j/rAAbp530XOgzOm4bgDtBMsx7BxolzY=,tag:pxz1id/FLauFLuXLYFEqrQ==,type:str]\n" +
                        "          status: ENC[AES256_GCM,data:pgjFEpAAFA==,iv:5+37Iz0vvzTeNYESjlC5tSzTy+OmYYp1YRyvzhJJK50=,tag:q8lQN+TdjYt31iz7beVVOw==,type:str]\n" +
                        "      restrisiko:\n" +
                        "        sannsynlighet: ENC[AES256_GCM,data:DmHuPw==,iv:WayHJMzWGKATwI4u1a+MJc1tunYWQFQL48sdqWRpziI=,tag:ys6bldRoPU/S6hA/PLD5cA==,type:float]\n" +
                        "        konsekvens: ENC[AES256_GCM,data:yjv/EIQ=,iv:CKZAp77moOiRyyI+v8eC9/fZi2A3N0rH7K1SECFKiQ8=,tag:M0a/1Sqm9tFOSBjGyW1l8A==,type:int]\n" +
                        "    - ID: ENC[AES256_GCM,data:bat7,iv:tQXLBAa70RmynxpWixP/NF0wBkp80VgBXoHBPlQRKKY=,tag:uhJy8KhqyShstdN13Z/Yvg==,type:str]\n" +
                        "      tittel: ENC[AES256_GCM,data:5RevTtMbjTYtdd1+zfi58k1cdbozaYE=,iv:jCcvrQ44an6awt42P245rkXU7vYaj6fp4o942hOeeTk=,tag:aLzHnk4bLQK2Qh4aPUczjQ==,type:str]\n" +
                        "      beskrivelse: ENC[AES256_GCM,data:nllLeNJCgXamkudzsGFw35fNkdpj3mO+HC7pQkszKZjxJhfdWEoiPEd6ZEitubux36gzY4BYVe+PhAM/awn7J9yYZiqxrRfkssQDjA==,iv:hr3LCfQ3FXmpmE4qT96lahEG45XEtjYeDgXBqs6znLY=,tag:6kRfj6lHUNNqiz1iKN1kRA==,type:str]\n" +
                        "      sistEndret: ENC[AES256_GCM,data:u1ZDiD3WgfrvXg==,iv:xiS/rgM9mHOVaqsyJFCz77Cg0hi4nCgrQkcK7kKr/sU=,tag:S4xeTQw09/lSdxL6s9W76w==,type:str]\n" +
                        "      trusselaktører:\n" +
                        "        - ENC[AES256_GCM,data:AA5ZQGrE8Z81lQcYkMs=,iv:Q0dM0MAEG+JjovcJcztVkipbxRtFr6ch4U+50ivOaKc=,tag:AdV/x8zfDCfYqia1EHPbmg==,type:str]\n" +
                        "        - ENC[AES256_GCM,data:adwa29aaOJD2g6/OvyQ=,iv:AN0p+eMtU6wmSxvEjqwIdXkmQ3037ok8G4mpvNqzhjU=,tag:5ZoTbNa49KXiEOeDyBeSkw==,type:str]\n" +
                        "      sårbarheter:\n" +
                        "        - ENC[AES256_GCM,data:YPQDUXCmjZ6WdyIT3VIxJC8O,iv:qhDrDXiCag/twtBAgNlkZMsSwnZbF1tSIHNHpqwLw8Y=,tag:TkVVccSHFiejYGPT4wqIOg==,type:str]\n" +
                        "      risiko:\n" +
                        "        oppsummering: ENC[AES256_GCM,data:AVVf16s+lrAi1AvUwufvQfMX81i0ECArQ8vGoPMLbRbD/SjNub3rvLwlIwfQjPyYUY9b9AsKQizaVBSRx/QTDkmdrgTBrUOt1edhdM74COU0l1Rcb4tuMg/nFgOZYcY+QrilDN/ka1HT+R1kxfUt6esA9T5PTcZzIKJL0m1auwoES/6cCoJcdlwPIXLk8va057jW4CB9xUYSkNYpo9O9+1Xu05PJrNnnApDNyjzlNibdthpaGwr0oQHGQL8BMCIc9OUJ7abGZnYmxyN/ffB4Owf0wxEW6VAOThz59kVhuiqJrJI=,iv:wC7khHw0ed2SFvsCDTR0yWNduqFXLGsLz9m57fFH1PE=,tag:UkO22jRGeglY/9szXqhfBw==,type:str]\n" +
                        "        sannsynlighet: ENC[AES256_GCM,data:ug2v,iv:tMLKS4WMgdOlgD0FGQ2N6ZBwRtsBWBoDUkV9Er+qwQI=,tag:7aJIDmIy+DeA/5/JP1Z3YQ==,type:float]\n" +
                        "        konsekvens: ENC[AES256_GCM,data:eHuPFoTpzIw=,iv:nnbTjNkXmp53lw/CWZobIBB65Bs2x8an3lp8gvtkrEY=,tag:AmeM3m4yR/SP3VsEi5xE8A==,type:int]\n" +
                        "      tiltak:\n" +
                        "        - ID: ENC[AES256_GCM,data:poXF,iv:3DFgao2jy/GzK7GQkADR4/olvJKGqL/Au9l/T5FfPUE=,tag:XseuJPLlxMjcZS+DiJ2pGQ==,type:str]\n" +
                        "          beskrivelse: ENC[AES256_GCM,data:pKd7zLdzfllcW2VPpRRqT5XXts2Mx+a94zGbC7vXmDzXjeETWH5G+0ts1SeZWp6eXLxYB+dp,iv:XCuPLOOX6la8JukZBrKuUaKrImJQFoHkO9P9BVW+6kk=,tag:Sg79/QVXIqiRa0Qsc33doQ==,type:str]\n" +
                        "          tiltakseier: ENC[AES256_GCM,data:kBkn39EmcXvYDw==,iv:/xWVudBAJq77J1uZmwf+XRdDCUGIYgBVJ7sDrn6Upz8=,tag:qk4jlJUMYDzZI+E7YKlFKw==,type:str]\n" +
                        "          status: ENC[AES256_GCM,data:bQ94oLhwC286TZU0,iv:QUuvAShsRP1FlEQCol3/G6z37ob4mZ9KqtCXuvXCp+w=,tag:mtuQVDRxAK1BtxR9SfTTeg==,type:str]\n" +
                        "          frist: ENC[AES256_GCM,data:DatSYtTCiVnlqw==,iv:VbOW8cHIKAC6YmPaSHe6vESYIQbWdQT1L+2Unjsf4g4=,tag:EQ1JHJxI1Spxa4LBNGvxsQ==,type:str]\n" +
                        "        - ID: ENC[AES256_GCM,data:ys/O,iv:9wQ04Z6zmBSu1cDatzv5HfIklGPmN6rVuHZV4LPXFz4=,tag:HU0xvyfdX9rLLRykPLz8jw==,type:str]\n" +
                        "          beskrivelse: ENC[AES256_GCM,data:7L4YaYVf8rMhO28hhlp+XfHIyThkl9+u++tQOFFyFI2JG5P4ZXnVbl/oxg70W/TT+bG2,iv:vjw78wIVmvID/00J3af3njErnKufC54RYOWCQ+UhZqE=,tag:FhjqY0llP6PWsB2+rkBR7g==,type:str]\n" +
                        "          tiltakseier: ENC[AES256_GCM,data:WmODCWVfi2fajkjBhGk=,iv:xL2ZroHMObV4t2HKkHJL58BDFbf0ogGN/3LK3Lu4rh8=,tag:JqrLt56IV+B0KmQ8W9geOw==,type:str]\n" +
                        "          frist: ENC[AES256_GCM,data:DatSYtTCiVnlqw==,iv:VbOW8cHIKAC6YmPaSHe6vESYIQbWdQT1L+2Unjsf4g4=,tag:EQ1JHJxI1Spxa4LBNGvxsQ==,type:str]\n" +
                        "          status: ENC[AES256_GCM,data:pgjFEpAAFA==,iv:5+37Iz0vvzTeNYESjlC5tSzTy+OmYYp1YRyvzhJJK50=,tag:q8lQN+TdjYt31iz7beVVOw==,type:str]\n" +
                        "        - ID: ENC[AES256_GCM,data:qG14,iv:A28A+pKzrEuRcIMZmb9UShospslJ9mpghv+rLqtRzMs=,tag:pxHhpuUh5uppdzKWLOHesg==,type:str]\n" +
                        "          beskrivelse: ENC[AES256_GCM,data:jZczTrMTmghWHfchy7RlYnsqwS5XBtTqK4H+6nCZsYSXDVtr0rG8V+DBtsiqyUvpMjIetOny9NRhlcMnM6GTF6c=,iv:onjy6bQTwQxn1FVD4ZHDDSQWpZmcLCnXvnezDPQsrx8=,tag:P7Z8CrYSQs1/qHzfIekz7Q==,type:str]\n" +
                        "          tiltakseier: ENC[AES256_GCM,data:WmODCWVfi2fajkjBhGk=,iv:xL2ZroHMObV4t2HKkHJL58BDFbf0ogGN/3LK3Lu4rh8=,tag:JqrLt56IV+B0KmQ8W9geOw==,type:str]\n" +
                        "          frist: ENC[AES256_GCM,data:DatSYtTCiVnlqw==,iv:VbOW8cHIKAC6YmPaSHe6vESYIQbWdQT1L+2Unjsf4g4=,tag:EQ1JHJxI1Spxa4LBNGvxsQ==,type:str]\n" +
                        "          status: ENC[AES256_GCM,data:bQ94oLhwC286TZU0,iv:QUuvAShsRP1FlEQCol3/G6z37ob4mZ9KqtCXuvXCp+w=,tag:mtuQVDRxAK1BtxR9SfTTeg==,type:str]\n" +
                        "      restrisiko:\n" +
                        "        sannsynlighet: ENC[AES256_GCM,data:DmHuPw==,iv:WayHJMzWGKATwI4u1a+MJc1tunYWQFQL48sdqWRpziI=,tag:ys6bldRoPU/S6hA/PLD5cA==,type:float]\n" +
                        "        konsekvens: ENC[AES256_GCM,data:gofoRnmYKQ==,iv:GXjlGCyuC4BiwNY3c7rGK72k4Smbcqm65oZDXahN9pk=,tag:DjoQh4JWqqAV3ZshnkAgsg==,type:int]\n" +
                        "sops:\n" +
                        "    kms: []\n" +
                        "    gcp_kms:\n" +
                        "        - resource_id: projects/spire-ros-5lmr/locations/eur4/keyRings/ROS/cryptoKeys/ros-as-code\n" +
                        "          created_at: \"2024-02-26T11:07:40Z\"\n" +
                        "          enc: CiQAMVUE/37OYtttbo2CdD8gpEO8TPxKcHkC11AR0hucyNrP7A0SSQDD8i3aMNv9aKrTOJNtjS7nD+iF99R6WigEO8rZTXO2QxI4dpeaOpaEZC27EF1KDPlC9EVuPdeM00/XcNJQZFMnCNx1Byhk25M=\n" +
                        "    azure_kv: []\n" +
                        "    hc_vault: []\n" +
                        "    age:\n" +
                        "        - recipient: age1ayuv56c98uukut752jpxl68tpch2yjvx5z3agk0t73h79kq3z4fstrtvqf\n" +
                        "          enc: |\n" +
                        "            -----BEGIN AGE ENCRYPTED FILE-----\n" +
                        "            YWdlLWVuY3J5cHRpb24ub3JnL3YxCi0+IFgyNTUxOSBEY1haSmhwc291TTJ3VVFi\n" +
                        "            RFNiUzM5Y2tvOGlpZzVPVUlXSWhHcDBURFRBCmVsWitqWUhJdm1lTkd0cnNMMTVH\n" +
                        "            K2hDK3FyeGtpcmlyYzhvZnpLU1l6Y2MKLS0tICtZNVBPNTZGQ09ha1ArYlBmR25J\n" +
                        "            N1I4V0hSU0hHVkVwd3Z3bzhqUXhJZWcKj+Jv8NBeHmWdPSjWinPbiiWbfBB9NLSz\n" +
                        "            RRObbeK0xEp9rmLi5XyDPq3GQ+XMq25Yy0dGT81ROlICeDsHQX+uHg==\n" +
                        "            -----END AGE ENCRYPTED FILE-----\n" +
                        "    lastmodified: \"2024-02-26T12:43:22Z\"\n" +
                        "    mac: ENC[AES256_GCM,data:BnS8cv9SLWsT6pW1cr7/9wAlObHvMvqHKubgjvXoQ5i4r5juVhx3WrFpfpNwpOx9C30efGYACkPZFaiAI7H+soajCozG3qpXkTNSnOcVa9GdNFpsusa34ETMndszGA1POM5+nDOPjofS3MAnY9/dVMHT0d38PXDif7u6Nv4Nhtg=,iv:lgjkDzG86NUplu1WRBvYbbg0IEoZtFDdmRFHQXxzppM=,tag:ORbwDa4O3mpkDBaUv1F+EA==,type:str]\n" +
                        "    pgp: []\n" +
                        "    unencrypted_suffix: _unencrypted\n" +
                        "    version: 3.8.1\n",
                gcpAccessToken = GCPAccessToken("bytt med: gcp-access-token"),
                System.getenv("SOPS_AGE_KEY")
            )
        }
    }
}
