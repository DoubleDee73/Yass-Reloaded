package yass

import spock.lang.Specification
import yass.integration.separation.SeparationResult
import yass.integration.transcription.openai.OpenAiTranscriptionResult
import yass.wizard.WizardTranscriptionState

class YassActionsWizardSpec extends Specification {

    def "offers post-wizard separation only when no separation result exists"() {
        expect:
        YassActions.shouldOfferSeparationAfterWizard(null)
        YassActions.shouldOfferSeparationAfterWizard(new WizardTranscriptionState(null, null, null, null, null))
        !YassActions.shouldOfferSeparationAfterWizard(new WizardTranscriptionState(
                null,
                null,
                null,
                new SeparationResult(null, null, null, null),
                null))
    }
}
