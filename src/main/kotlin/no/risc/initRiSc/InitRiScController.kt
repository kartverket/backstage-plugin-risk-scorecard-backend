package no.risc.initRiSc

import no.risc.initRiSc.model.RiScTypeDescriptor
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/initrisc")
class InitRiScController(
    private val initRiScServiceIntegration: InitRiScServiceIntegration,
) {
    @GetMapping
    suspend fun getAllDefaultRiScTypeDescriptors(): List<RiScTypeDescriptor> = initRiScServiceIntegration.fetchDefaultRiScTypeDescriptors()
}
