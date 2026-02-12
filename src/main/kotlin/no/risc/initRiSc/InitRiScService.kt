package no.risc.initRiSc

import no.risc.infra.connector.models.AccessTokens
import no.risc.initRiSc.model.RiScTypeDescriptor
import no.risc.risc.models.RiSc5X

interface InitRiScService {
    /**
     * Returns an initial RiSc of some ID. Use the getInitRiScDescriptors method to return a list of valid IDs.
     *
     * @param initRiScId ID of initial RiSc.
     *
     * @param initialContent A JSON serialized RiSc to base the initial RiSc on. initialContent must include the
     *                    `title` and `scope` fields. These are the only fields used from initialContent.
     *
     * @param accessTokens Access tokens (GitHub and GCP).
     */
    suspend fun getInitRiSc(
        initRiScId: String,
        initialContent: String,
        accessTokens: AccessTokens,
    ): String

    /**
     * Gets a list of RiSc descriptors. Each RiSc descriptor describes an available initial RiSc with properties
     * including ID, name/description displayed in the ui (listName/listDescription), the total number of scenarios, and
     * the total number of actions.
     */
    suspend fun getInitRiScDescriptors(accessTokens: AccessTokens): List<RiScTypeDescriptor>
}
