package cz.obehy.lint

import com.android.tools.lint.client.api.IssueRegistry
import com.android.tools.lint.client.api.Vendor
import com.android.tools.lint.detector.api.CURRENT_API
import com.android.tools.lint.detector.api.Issue

/** Registers the Oběhy checks with Android Lint (see the Lint-Registry-v2 manifest entry). */
class ObehyIssueRegistry : IssueRegistry() {
    override val issues: List<Issue> = DesignSystemDetector.ISSUES
    override val api: Int = CURRENT_API
    override val minApi: Int = 14
    override val vendor = Vendor(vendorName = "Oběhy", feedbackUrl = "https://github.com/Sap1k/obehy-app/issues")
}
