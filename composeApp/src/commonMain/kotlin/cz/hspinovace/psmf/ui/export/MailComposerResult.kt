package cz.hspinovace.psmf.ui.export

/**
 * The four results iOS's mail composer can finish with, as plain values, so
 * that [mailComposerOutcome] can be tested where tests run.
 */
enum class MailComposerResult { SENT, SAVED, CANCELLED, FAILED }
