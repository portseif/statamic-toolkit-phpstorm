package com.antlers.support.formatting

import com.intellij.openapi.actionSystem.IdeActions
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.codeStyle.CodeStyleManager
import com.intellij.testFramework.fixtures.BasePlatformTestCase

class AntlersFormattingPostProcessorTest : BasePlatformTestCase() {

    fun testReformatAlignsElseWithIfAndIndentsStandaloneAntlersTagsInsideBranch() {
        myFixture.configureByText(
            "demo.antlers.html",
            """
            {{ if site:environment==='production'}}
            {{ else }}
            {{ partial:partials/sections/contact-form }}
            {{ /if }}
            """.trimIndent()
        )

        reformatCurrentFile()

        myFixture.checkResult(
            """
            {{ if site:environment === 'production' }}
            {{ else }}
                {{ partial:partials/sections/contact-form }}
            {{ /if }}
            """.trimIndent()
        )
    }

    fun testReformatIndentsNestedConditionalBranches() {
        myFixture.configureByText(
            "demo.antlers.html",
            """
            {{ if outer }}
            {{ if inner }}
            {{ partial:components/admin }}
            {{ /if }}
            {{ else }}
            {{ partial:components/fallback }}
            {{ /if }}
            """.trimIndent()
        )

        reformatCurrentFile()

        myFixture.checkResult(
            """
            {{ if outer }}
                {{ if inner }}
                    {{ partial:components/admin }}
                {{ /if }}
            {{ else }}
                {{ partial:components/fallback }}
            {{ /if }}
            """.trimIndent()
        )
    }

    fun testReformatIsIdempotentForConditionalIndentation() {
        myFixture.configureByText(
            "demo.antlers.html",
            """
            <div>
                {{ if logged_in }}
                    {{ partial:components/account }}
                {{ /if }}
            </div>
            """.trimIndent()
        )

        reformatCurrentFile()
        reformatCurrentFile()

        myFixture.checkResult(
            """
            <div>
                {{ if logged_in }}
                    {{ partial:components/account }}
                {{ /if }}
            </div>
            """.trimIndent()
        )
    }

    fun testReformatDoesNotNestSequentialStandalonePartials() {
        myFixture.configureByText(
            "demo.antlers.html",
            """
            <main>
                {{ partial:partials/sections/hero }}
                {{ partial:partials/sections/logo-cloud }}
                {{ partial:partials/sections/bento }}
            </main>
            """.trimIndent()
        )

        reformatCurrentFile()

        myFixture.checkResult(
            """
            <main>
                {{ partial:partials/sections/hero }}
                {{ partial:partials/sections/logo-cloud }}
                {{ partial:partials/sections/bento }}
            </main>
            """.trimIndent()
        )
    }

    fun testReformatFlattensSequentialStandalonePartialsAtRootLevel() {
        myFixture.configureByText(
            "demo.antlers.html",
            """
            {{ partial:partials/sections/hero }}
                {{ partial:partials/sections/logo-cloud }}
                {{ partial:partials/sections/bento }}
            """.trimIndent()
        )

        reformatCurrentFile()

        myFixture.checkResult(
            """
            {{ partial:partials/sections/hero }}
            {{ partial:partials/sections/logo-cloud }}
            {{ partial:partials/sections/bento }}
            """.trimIndent()
        )
    }

    fun testReformatFlattensSequentialStandalonePartialsInsideHtml() {
        myFixture.configureByText(
            "demo.antlers.html",
            """
            <main>
                {{ partial:partials/sections/hero }}
                    {{ partial:partials/sections/logo-cloud }}
                    {{ partial:partials/sections/bento }}
            </main>
            """.trimIndent()
        )

        reformatCurrentFile()

        myFixture.checkResult(
            """
            <main>
                {{ partial:partials/sections/hero }}
                {{ partial:partials/sections/logo-cloud }}
                {{ partial:partials/sections/bento }}
            </main>
            """.trimIndent()
        )
    }

    fun testReformatPreservesHtmlParentIndentAroundStandaloneAntlersBlocks() {
        myFixture.configureByText(
            "demo.antlers.html",
            """
            <div class="font-display">
            <header>
            {{ partial:partials/nav }}
            </header>

            <main id="main-content">
            {{ partial:partials/sections/hero }}
            {{ partial:partials/sections/logo-cloud }}
            {{ partial:partials/sections/bento }}
            </main>

            {{ if site:environment==='production'}}
            {{ else }}
            {{ partial:partials/sections/contact-form }}
            {{ /if }}

            {{ partial:partials/sections/footer }}
            </div>
            """.trimIndent()
        )

        reformatCurrentFile()

        myFixture.checkResult(
            """
            <div class="font-display">
                <header>
                    {{ partial:partials/nav }}
                </header>

                <main id="main-content">
                    {{ partial:partials/sections/hero }}
                    {{ partial:partials/sections/logo-cloud }}
                    {{ partial:partials/sections/bento }}
                </main>

                {{ if site:environment === 'production' }}
                {{ else }}
                    {{ partial:partials/sections/contact-form }}
                {{ /if }}

                {{ partial:partials/sections/footer }}
            </div>
            """.trimIndent()
        )
    }

    fun testReformatIndentsNestedCollectionEntryBlocksWithHtmlContent() {
        myFixture.configureByText(
            "demo.antlers.html",
            """
            {{ collection:posts }}
            {{ entry }}
            <h2>{{ title }}</h2>
            <p>{{ excerpt }}</p>
            {{ /entry }}
            {{ /collection:posts }}
            """.trimIndent()
        )

        reformatCurrentFile()

        myFixture.checkResult(
            """
            {{ collection:posts }}
                {{ entry }}
                    <h2>{{ title }}</h2>
                    <p>{{ excerpt }}</p>
                {{ /entry }}
            {{ /collection:posts }}
            """.trimIndent()
        )
    }

    fun testReformatPreservesJavascriptIndentInsideScriptTags() {
        myFixture.configureByText(
            "demo.antlers.html",
            """
            <div>
                <script>
            document.addEventListener('alpine:init', () => {
            Alpine.data('demoForm', () => ({
            async submitForm() {
            if (window.gtag_report_conversion) {
            gtag_report_conversion()
            }
            },
            }))
            })
                </script>
            </div>
            """.trimIndent()
        )

        reformatCurrentFile()

        myFixture.checkResult(
            """
            <div>
                <script>
                    document.addEventListener('alpine:init', () => {
                        Alpine.data('demoForm', () => ({
                            async submitForm() {
                                if (window.gtag_report_conversion) {
                                    gtag_report_conversion()
                                }
                            },
                        }))
                    })
                </script>
            </div>
            """.trimIndent()
        )
    }

    fun testReformatPreservesNestedJavascriptClosersInsideScriptTags() {
        myFixture.configureByText(
            "demo.antlers.html",
            """
            <div>
                <script>
            document.addEventListener('alpine:init', () => {
            Alpine.data('chilipiperDemoForm', () => ({
            async submitForm() {
            if (window.ChiliPiper) {
            window.ChiliPiper.submit({
            lead: {
            company: this.formData.company,
            jobTitle: this.formData.jobTitle,
            },
            onSuccess: () => {
            if (typeof gtag_report_conversion === 'function') {
            gtag_report_conversion()
            }
            },
            onError: () => {
            this.errorMessage = 'Something went wrong. Please try again.'
            this.state = 'form'
            },
            onClose: () => {
            this.state = 'form'
            },
            })
            } else {
            console.error('ChiliPiper Concierge SDK not loaded')
            this.errorMessage = 'Scheduling is temporarily unavailable. Please try again.'
            this.state = 'form'
            }
            },
            }))
            })
                </script>
            </div>
            """.trimIndent()
        )

        reformatCurrentFile()

        myFixture.checkResult(
            """
            <div>
                <script>
                    document.addEventListener('alpine:init', () => {
                        Alpine.data('chilipiperDemoForm', () => ({
                            async submitForm() {
                                if (window.ChiliPiper) {
                                    window.ChiliPiper.submit({
                                        lead: {
                                            company: this.formData.company,
                                            jobTitle: this.formData.jobTitle,
                                        },
                                        onSuccess: () => {
                                            if (typeof gtag_report_conversion === 'function') {
                                                gtag_report_conversion()
                                            }
                                        },
                                        onError: () => {
                                            this.errorMessage = 'Something went wrong. Please try again.'
                                            this.state = 'form'
                                        },
                                        onClose: () => {
                                            this.state = 'form'
                                        },
                                    })
                                } else {
                                    console.error('ChiliPiper Concierge SDK not loaded')
                                    this.errorMessage = 'Scheduling is temporarily unavailable. Please try again.'
                                    this.state = 'form'
                                }
                            },
                        }))
                    })
                </script>
            </div>
            """.trimIndent()
        )
    }

    fun testReformatPreservesNestedJavascriptClosersInsideScriptTagsInAntlersPhpFiles() {
        myFixture.configureByText(
            "demo.antlers.php",
            """
            <div>
                <script>
            document.addEventListener('alpine:init', () => {
            Alpine.data('chilipiperDemoForm', () => ({
            async submitForm() {
            if (window.ChiliPiper) {
            window.ChiliPiper.submit({
            lead: {
            company: this.formData.company,
            jobTitle: this.formData.jobTitle,
            },
            onSuccess: () => {
            if (typeof gtag_report_conversion === 'function') {
            gtag_report_conversion()
            }
            },
            onError: () => {
            this.errorMessage = 'Something went wrong. Please try again.'
            this.state = 'form'
            },
            onClose: () => {
            this.state = 'form'
            },
            })
            } else {
            console.error('ChiliPiper Concierge SDK not loaded')
            this.errorMessage = 'Scheduling is temporarily unavailable. Please try again.'
            this.state = 'form'
            }
            },
            }))
            })
                </script>
            </div>
            """.trimIndent()
        )

        reformatCurrentFile()

        myFixture.checkResult(
            """
            <div>
                <script>
                    document.addEventListener('alpine:init', () => {
                        Alpine.data('chilipiperDemoForm', () => ({
                            async submitForm() {
                                if (window.ChiliPiper) {
                                    window.ChiliPiper.submit({
                                        lead: {
                                            company: this.formData.company,
                                            jobTitle: this.formData.jobTitle,
                                        },
                                        onSuccess: () => {
                                            if (typeof gtag_report_conversion === 'function') {
                                                gtag_report_conversion()
                                            }
                                        },
                                        onError: () => {
                                            this.errorMessage = 'Something went wrong. Please try again.'
                                            this.state = 'form'
                                        },
                                        onClose: () => {
                                            this.state = 'form'
                                        },
                                    })
                                } else {
                                    console.error('ChiliPiper Concierge SDK not loaded')
                                    this.errorMessage = 'Scheduling is temporarily unavailable. Please try again.'
                                    this.state = 'form'
                                }
                            },
                        }))
                    })
                </script>
            </div>
            """.trimIndent()
        )
    }

    fun testReformatPreservesJavascriptIndentInsideScriptTagsWithEmbeddedAntlersStrings() {
        myFixture.configureByText(
            "demo.antlers.html",
            """
            <script>
            document.addEventListener('alpine:init', () => {
            Alpine.data('chilipiperDemoForm', () => ({
              state: 'form',
              errorMessage: '',
              formData: {
                firstName: '',
                lastName: '',
                email: '',
                company: '',
                jobTitle: '',
              },

            async submitForm() {
              this.errorMessage = ''

              if (!this.formData.firstName || !this.formData.lastName || !this.formData.email || !this.formData.company) {
                this.errorMessage = 'Please fill in all required fields.'
                return
              }

            const hubspotPortalId = '{{ hubspot_portal_id ?? "8002304" }}'
            const hubspotFormId = '{{ hubspot_form_id ?? "aa9833ef-b387-4698-bba0-0bde60386f0a" }}'
            const cpDomain = '{{ chilipiper_domain ?? "onboard" }}'
            const cpRouter = '{{ chilipiper_router ?? "onboard-demo-2" }}'

              // Fire HubSpot submission in background (don't await — no need to block ChiliPiper)
              if (hubspotFormId) {
                fetch(
                        `https://api.hsforms.com/submissions/v3/integration/submit/${'$'}{hubspotPortalId}/${'$'}{hubspotFormId}`,
                        {
                          method: 'POST',
                          headers: {'Content-Type': 'application/json'},
                          body: JSON.stringify({
                            fields: [
                              {objectTypeId: '0-1', name: 'email', value: this.formData.email},
                              {objectTypeId: '0-1', name: 'firstName', value: this.formData.firstName},
                              {objectTypeId: '0-1', name: 'lastName', value: this.formData.lastName},
                              {objectTypeId: '0-1', name: 'company', value: this.formData.company},
                              {objectTypeId: '0-1', name: 'jobTitle', value: this.formData.jobTitle},
                            ],
                            legalConsentOptions: {
                              consent: {
                                consentToProcess: true,
                                text: 'I agree to allow Onboard to store and process my personal data.',
                              },
                            },
                          }),
                        },
                ).catch(error => console.error('HubSpot submission error:', error))
              }

              // Trigger ChiliPiper Concierge scheduling popup immediately (parallel with HubSpot)
              if (typeof ChiliPiper !== 'undefined') {
                ChiliPiper.submit(cpDomain, cpRouter, {
                  trigger: 'ThirdPartyForm',
                  lead: {
                    firstName: this.formData.firstName,
                    lastName: this.formData.lastName,
                    email: this.formData.email,
                    company: this.formData.company,
                    jobTitle: this.formData.jobTitle,
                  },
                  onSuccess: () => {
                    if (typeof gtag_report_conversion === 'function') {
                      gtag_report_conversion()
                    }
                  },
                  onError: () => {
                    this.errorMessage = 'Something went wrong. Please try again.'
                    this.state = 'form'
                  },
                  onClose: () => {
                    this.state = 'form'
                  },
                })
              } else {
                console.error('ChiliPiper Concierge SDK not loaded')
                this.errorMessage = 'Scheduling is temporarily unavailable. Please try again.'
                this.state = 'form'
              }
            },
            }))
            })
            </script>
            """.trimIndent()
        )

        reformatCurrentFile()
        myFixture.checkResult(
            """
            <script>
                document.addEventListener('alpine:init', () => {
                    Alpine.data('chilipiperDemoForm', () => ({
                        state: 'form',
                        errorMessage: '',
                        formData: {
                            firstName: '',
                            lastName: '',
                            email: '',
                            company: '',
                            jobTitle: '',
                        },

                        async submitForm() {
                            this.errorMessage = ''

                            if (!this.formData.firstName || !this.formData.lastName || !this.formData.email || !this.formData.company) {
                                this.errorMessage = 'Please fill in all required fields.'
                                return
                            }

                            const hubspotPortalId = '{{ hubspot_portal_id ?? "8002304" }}'
                            const hubspotFormId = '{{ hubspot_form_id ?? "aa9833ef-b387-4698-bba0-0bde60386f0a" }}'
                            const cpDomain = '{{ chilipiper_domain ?? "onboard" }}'
                            const cpRouter = '{{ chilipiper_router ?? "onboard-demo-2" }}'

                            // Fire HubSpot submission in background (don't await — no need to block ChiliPiper)
                            if (hubspotFormId) {
                                fetch(
                                `https://api.hsforms.com/submissions/v3/integration/submit/${'$'}{hubspotPortalId}/${'$'}{hubspotFormId}`,
                                {
                                    method: 'POST',
                                    headers: {'Content-Type': 'application/json'},
                                    body: JSON.stringify({
                                        fields: [
                                        {objectTypeId: '0-1', name: 'email', value: this.formData.email},
                                        {objectTypeId: '0-1', name: 'firstName', value: this.formData.firstName},
                                        {objectTypeId: '0-1', name: 'lastName', value: this.formData.lastName},
                                        {objectTypeId: '0-1', name: 'company', value: this.formData.company},
                                        {objectTypeId: '0-1', name: 'jobTitle', value: this.formData.jobTitle},
                                        ],
                                        legalConsentOptions: {
                                            consent: {
                                                consentToProcess: true,
                                                text: 'I agree to allow Onboard to store and process my personal data.',
                                            },
                                        },
                                    }),
                                },
                                ).catch(error => console.error('HubSpot submission error:', error))
                            }

                            // Trigger ChiliPiper Concierge scheduling popup immediately (parallel with HubSpot)
                            if (typeof ChiliPiper !== 'undefined') {
                                ChiliPiper.submit(cpDomain, cpRouter, {
                                    trigger: 'ThirdPartyForm',
                                    lead: {
                                        firstName: this.formData.firstName,
                                        lastName: this.formData.lastName,
                                        email: this.formData.email,
                                        company: this.formData.company,
                                        jobTitle: this.formData.jobTitle,
                                    },
                                    onSuccess: () => {
                                        if (typeof gtag_report_conversion === 'function') {
                                            gtag_report_conversion()
                                        }
                                    },
                                    onError: () => {
                                        this.errorMessage = 'Something went wrong. Please try again.'
                                        this.state = 'form'
                                    },
                                    onClose: () => {
                                        this.state = 'form'
                                    },
                                })
                            } else {
                                console.error('ChiliPiper Concierge SDK not loaded')
                                this.errorMessage = 'Scheduling is temporarily unavailable. Please try again.'
                                this.state = 'form'
                            }
                        },
                    }))
                })
            </script>
            """.trimIndent()
        )
    }

    fun testReformatPreservesJavascriptIndentInsideScriptTagsWithoutEmbeddedAntlersStrings() {
        myFixture.configureByText(
            "demo.antlers.html",
            """
            <script>
            document.addEventListener('alpine:init', () => {
            Alpine.data('chilipiperDemoForm', () => ({
              state: 'form',
              errorMessage: '',
              formData: {
                firstName: '',
                lastName: '',
                email: '',
                company: '',
                jobTitle: '',
              },

            async submitForm() {
              this.errorMessage = ''

              if (!this.formData.firstName || !this.formData.lastName || !this.formData.email || !this.formData.company) {
                this.errorMessage = 'Please fill in all required fields.'
                return
              }

            const hubspotPortalId = '8002304'
            const hubspotFormId = 'aa9833ef-b387-4698-bba0-0bde60386f0a'
            const cpDomain = 'onboard'
            const cpRouter = 'onboard-demo-2'

              if (hubspotFormId) {
                fetch(
                        `https://api.hsforms.com/submissions/v3/integration/submit/${'$'}{hubspotPortalId}/${'$'}{hubspotFormId}`,
                        {
                          method: 'POST',
                          headers: {'Content-Type': 'application/json'},
                          body: JSON.stringify({
                            fields: [
                              {objectTypeId: '0-1', name: 'email', value: this.formData.email},
                              {objectTypeId: '0-1', name: 'firstName', value: this.formData.firstName},
                              {objectTypeId: '0-1', name: 'lastName', value: this.formData.lastName},
                              {objectTypeId: '0-1', name: 'company', value: this.formData.company},
                              {objectTypeId: '0-1', name: 'jobTitle', value: this.formData.jobTitle},
                            ],
                          }),
                        },
                ).catch(error => console.error('HubSpot submission error:', error))
              }

              if (typeof ChiliPiper !== 'undefined') {
                ChiliPiper.submit(cpDomain, cpRouter, {
                  trigger: 'ThirdPartyForm',
                  lead: {
                    firstName: this.formData.firstName,
                    lastName: this.formData.lastName,
                    email: this.formData.email,
                    company: this.formData.company,
                    jobTitle: this.formData.jobTitle,
                  },
                  onSuccess: () => {
                    if (typeof gtag_report_conversion === 'function') {
                      gtag_report_conversion()
                    }
                  },
                  onError: () => {
                    this.errorMessage = 'Something went wrong. Please try again.'
                    this.state = 'form'
                  },
                  onClose: () => {
                    this.state = 'form'
                  },
                })
              } else {
                console.error('ChiliPiper Concierge SDK not loaded')
                this.errorMessage = 'Scheduling is temporarily unavailable. Please try again.'
                this.state = 'form'
              }
            },
            }))
            })
            </script>
            """.trimIndent()
        )

        reformatCurrentFile()

        myFixture.checkResult(
            """
            <script>
                document.addEventListener('alpine:init', () => {
                    Alpine.data('chilipiperDemoForm', () => ({
                        state: 'form',
                        errorMessage: '',
                        formData: {
                            firstName: '',
                            lastName: '',
                            email: '',
                            company: '',
                            jobTitle: '',
                        },

                        async submitForm() {
                            this.errorMessage = ''

                            if (!this.formData.firstName || !this.formData.lastName || !this.formData.email || !this.formData.company) {
                                this.errorMessage = 'Please fill in all required fields.'
                                return
                            }

                            const hubspotPortalId = '8002304'
                            const hubspotFormId = 'aa9833ef-b387-4698-bba0-0bde60386f0a'
                            const cpDomain = 'onboard'
                            const cpRouter = 'onboard-demo-2'

                            if (hubspotFormId) {
                                fetch(
                                        `https://api.hsforms.com/submissions/v3/integration/submit/${'$'}{hubspotPortalId}/${'$'}{hubspotFormId}`,
                                        {
                                            method: 'POST',
                                            headers: {'Content-Type': 'application/json'},
                                            body: JSON.stringify({
                                                fields: [
                                                    {objectTypeId: '0-1', name: 'email', value: this.formData.email},
                                                    {objectTypeId: '0-1', name: 'firstName', value: this.formData.firstName},
                                                    {objectTypeId: '0-1', name: 'lastName', value: this.formData.lastName},
                                                    {objectTypeId: '0-1', name: 'company', value: this.formData.company},
                                                    {objectTypeId: '0-1', name: 'jobTitle', value: this.formData.jobTitle},
                                                ],
                                            }),
                                        },
                                ).catch(error => console.error('HubSpot submission error:', error))
                            }

                            if (typeof ChiliPiper !== 'undefined') {
                                ChiliPiper.submit(cpDomain, cpRouter, {
                                    trigger: 'ThirdPartyForm',
                                    lead: {
                                        firstName: this.formData.firstName,
                                        lastName: this.formData.lastName,
                                        email: this.formData.email,
                                        company: this.formData.company,
                                        jobTitle: this.formData.jobTitle,
                                    },
                                    onSuccess: () => {
                                        if (typeof gtag_report_conversion === 'function') {
                                            gtag_report_conversion()
                                        }
                                    },
                                    onError: () => {
                                        this.errorMessage = 'Something went wrong. Please try again.'
                                        this.state = 'form'
                                    },
                                    onClose: () => {
                                        this.state = 'form'
                                    },
                                })
                            } else {
                                console.error('ChiliPiper Concierge SDK not loaded')
                                this.errorMessage = 'Scheduling is temporarily unavailable. Please try again.'
                                this.state = 'form'
                            }
                        },
                    }))
                })
            </script>
            """.trimIndent()
        )
    }

    fun testEnterInsideScriptIndentsAfterDocumentAddEventListener() {
        myFixture.configureByText(
            "demo.antlers.html",
            """
            <script>
                document.addEventListener('alpine:init', () => {<caret>
                })
            </script>
            """.trimIndent()
        )

        myFixture.performEditorAction(IdeActions.ACTION_EDITOR_ENTER)

        myFixture.checkResult(
            """
            <script>
                document.addEventListener('alpine:init', () => {
                    <caret>
                })
            </script>
            """.trimIndent()
        )
    }

    fun testEnterInsideScriptIndentsAfterAlpineDataObjectLiteral() {
        myFixture.configureByText(
            "demo.antlers.html",
            """
            <script>
                Alpine.data('chilipiperDemoForm', () => ({<caret>
                }))
            </script>
            """.trimIndent()
        )

        myFixture.performEditorAction(IdeActions.ACTION_EDITOR_ENTER)

        myFixture.checkResult(
            """
            <script>
                Alpine.data('chilipiperDemoForm', () => ({
                    <caret>
                }))
            </script>
            """.trimIndent()
        )
    }

    fun testEnterInsideScriptDedentsAfterNestedJsBlockClose() {
        myFixture.configureByText(
            "demo.antlers.html",
            """
            <script>
                document.addEventListener('alpine:init', () => {
                    Alpine.data('chilipiperDemoForm', () => ({
                        async submitForm() {
                            if (window.ChiliPiper) {
                                window.ChiliPiper.submit()
                            }<caret>
                        },
                    }))
                })
            </script>
            """.trimIndent()
        )

        myFixture.performEditorAction(IdeActions.ACTION_EDITOR_ENTER)

        myFixture.checkResult(
            """
            <script>
                document.addEventListener('alpine:init', () => {
                    Alpine.data('chilipiperDemoForm', () => ({
                        async submitForm() {
                            if (window.ChiliPiper) {
                                window.ChiliPiper.submit()
                            }
                            <caret>
                        },
                    }))
                })
            </script>
            """.trimIndent()
        )
    }

    fun testEnterInsideScriptDedentsAfterJsMethodCloser() {
        myFixture.configureByText(
            "demo.antlers.html",
            """
            <script>
                document.addEventListener('alpine:init', () => {
                    Alpine.data('chilipiperDemoForm', () => ({
                        async submitForm() {
                            this.state = 'form'
                        },<caret>
                    }))
                })
            </script>
            """.trimIndent()
        )

        myFixture.performEditorAction(IdeActions.ACTION_EDITOR_ENTER)

        myFixture.checkResult(
            """
            <script>
                document.addEventListener('alpine:init', () => {
                    Alpine.data('chilipiperDemoForm', () => ({
                        async submitForm() {
                            this.state = 'form'
                        },
                        <caret>
                    }))
                })
            </script>
            """.trimIndent()
        )
    }

    fun testEnterInsideScriptDedentsAfterReturnedObjectCloser() {
        myFixture.configureByText(
            "demo.antlers.html",
            """
            <script>
                document.addEventListener('alpine:init', () => {
                    Alpine.data('chilipiperDemoForm', () => ({
                        state: 'form',
                    }))<caret>
                })
            </script>
            """.trimIndent()
        )

        myFixture.performEditorAction(IdeActions.ACTION_EDITOR_ENTER)

        myFixture.checkResult(
            """
            <script>
                document.addEventListener('alpine:init', () => {
                    Alpine.data('chilipiperDemoForm', () => ({
                        state: 'form',
                    }))
                    <caret>
                })
            </script>
            """.trimIndent()
        )
    }

    fun testEnterInsideScriptStaysInsideIfAfterCallbackObjectClose() {
        myFixture.configureByText(
            "demo.antlers.html",
            """
            <script>
                document.addEventListener('alpine:init', () => {
                    Alpine.data('chilipiperDemoForm', () => ({
                        async submitForm() {
                            if (window.ChiliPiper) {
                                window.ChiliPiper.submit({
                                    onClose: () => {
                                        this.state = 'form'
                                    },
                                })<caret>
                            }
                        },
                    }))
                })
            </script>
            """.trimIndent()
        )

        myFixture.performEditorAction(IdeActions.ACTION_EDITOR_ENTER)

        myFixture.checkResult(
            """
            <script>
                document.addEventListener('alpine:init', () => {
                    Alpine.data('chilipiperDemoForm', () => ({
                        async submitForm() {
                            if (window.ChiliPiper) {
                                window.ChiliPiper.submit({
                                    onClose: () => {
                                        this.state = 'form'
                                    },
                                })
                                <caret>
                            }
                        },
                    }))
                })
            </script>
            """.trimIndent()
        )
    }

    private fun reformatCurrentFile() {
        PsiDocumentManager.getInstance(project).commitAllDocuments()
        WriteCommandAction.runWriteCommandAction(project) {
            CodeStyleManager.getInstance(project).reformat(myFixture.file)
        }
        PsiDocumentManager.getInstance(project).commitAllDocuments()
    }
}
