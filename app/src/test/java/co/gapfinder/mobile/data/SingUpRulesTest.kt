package co.gapfinder.mobile.domain.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class SignUpRulesTest {
    @Test fun `email without user is rejected`() = assertNotNull(SignUpRules.emailProblem("@a.com"))
    @Test fun `email of another domain is rejected`() = assertNotNull(SignUpRules.emailProblem("ana@uniandes.edu.co"))
    @Test fun `email with two dots in a row is rejected`() = assertNotNull(SignUpRules.emailProblem("ana..g@gmail.com"))
    @Test fun `gmail and outlook are accepted`() {
        assertNull(SignUpRules.emailProblem("ana.gomez@gmail.com"))
        assertNull(SignUpRules.emailProblem("ana_g@outlook.com"))
    }
    @Test fun `name of 51 letters is rejected`() = assertNotNull(SignUpRules.nameProblem("a".repeat(51)))
    @Test fun `name of 50 letters is accepted`() = assertNull(SignUpRules.nameProblem("a".repeat(50)))
    @Test fun `name with numbers is rejected`() = assertNotNull(SignUpRules.nameProblem("Ana 2"))
    @Test fun `name with accents is accepted`() = assertNull(SignUpRules.nameProblem("Sofía Muñoz"))
    @Test fun `extra spaces are cleaned`() = assertEquals("Ana Gomez", SignUpRules.cleanName("  Ana    Gomez "))
    @Test fun `phone must start with 3`() {
        assertNotNull(SignUpRules.phoneProblem("2101234567"))
        assertNull(SignUpRules.phoneProblem("3101234567"))
    }
}