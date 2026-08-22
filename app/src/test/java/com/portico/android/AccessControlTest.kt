package com.portico.android.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Roles, permissions and plan limits.
 *
 * These are the rules that decide what somebody may do and how much they may
 * hold. The bridge enforces the same ladder server-side, so what is checked
 * here is that the client's model agrees with it: a UI that grants more than
 * the server allows produces controls that fail, and one that grants less hides
 * features people are paying for.
 */
class AccessControlTest {

    // ------------------------------------------------------------ role ranks

    @Test
    fun `the ladder is ordered owner down to viewer`() {
        assertEquals(4, OrgRole.OWNER.rank)
        assertEquals(3, OrgRole.ADMIN.rank)
        assertEquals(2, OrgRole.ANALYST.rank)
        assertEquals(1, OrgRole.VIEWER.rank)

        val descending = OrgRole.entries.sortedByDescending { it.rank }
        assertEquals(
            listOf(OrgRole.OWNER, OrgRole.ADMIN, OrgRole.ANALYST, OrgRole.VIEWER),
            descending
        )
    }

    @Test
    fun `an owner holds every permission`() {
        Permission.entries.forEach { permission ->
            assertTrue("owner denied ${permission.name}", OrgRole.OWNER.holds(permission))
        }
    }

    @Test
    fun `a viewer may read and nothing else`() {
        assertTrue(OrgRole.VIEWER.holds(Permission.VIEW_PORTFOLIO))
        assertTrue(OrgRole.VIEWER.holds(Permission.VIEW_REPORTS))

        assertFalse(OrgRole.VIEWER.holds(Permission.EDIT_PROPERTY))
        assertFalse(OrgRole.VIEWER.holds(Permission.RECORD_FINANCIALS))
        assertFalse(OrgRole.VIEWER.holds(Permission.MANAGE_DOCUMENTS))
        assertFalse(OrgRole.VIEWER.holds(Permission.MANAGE_MEMBERS))
        assertFalse(OrgRole.VIEWER.holds(Permission.EXPORT_DATA))
        assertFalse(OrgRole.VIEWER.holds(Permission.BILLING))
    }

    @Test
    fun `an analyst may record figures but not manage people or billing`() {
        assertTrue(OrgRole.ANALYST.holds(Permission.EDIT_PROPERTY))
        assertTrue(OrgRole.ANALYST.holds(Permission.RECORD_FINANCIALS))

        assertFalse(OrgRole.ANALYST.holds(Permission.MANAGE_MEMBERS))
        assertFalse(OrgRole.ANALYST.holds(Permission.EXPORT_DATA))
        assertFalse(OrgRole.ANALYST.holds(Permission.BILLING))
    }

    @Test
    fun `only an owner may touch billing`() {
        assertTrue(OrgRole.OWNER.holds(Permission.BILLING))
        listOf(OrgRole.ADMIN, OrgRole.ANALYST, OrgRole.VIEWER).forEach {
            assertFalse("${it.name} must not hold BILLING", it.holds(Permission.BILLING))
        }
    }

    @Test
    fun `an admin manages members and documents but not billing`() {
        assertTrue(OrgRole.ADMIN.holds(Permission.MANAGE_MEMBERS))
        assertTrue(OrgRole.ADMIN.holds(Permission.MANAGE_DOCUMENTS))
        assertTrue(OrgRole.ADMIN.holds(Permission.EXPORT_DATA))
        assertFalse(OrgRole.ADMIN.holds(Permission.BILLING))
    }

    @Test
    fun `a senior role never holds less than a junior one`() {
        // The whole point of a rank ladder: permissions accumulate downward.
        Permission.entries.forEach { permission ->
            OrgRole.entries.forEach { role ->
                if (role.holds(permission)) {
                    OrgRole.entries.filter { it.rank > role.rank }.forEach { senior ->
                        assertTrue(
                            "${senior.name} holds less than ${role.name} for ${permission.name}",
                            senior.holds(permission)
                        )
                    }
                }
            }
        }
    }

    @Test
    fun `an unrecognised stored role degrades to the least privilege`() {
        val member = OrganizationMember(
            id = "m1", organizationId = "o1", name = "Someone",
            email = "someone@example.com", role = "SUPREME_LEADER", joinedAt = ""
        )
        assertEquals(
            "an unknown role must not be trusted with anything",
            OrgRole.VIEWER, member.orgRole
        )
    }

    // ------------------------------------------------------------ plan tiers

    @Test
    fun `the free tier stops at two properties and pro does not`() {
        assertEquals(2, PlanTier.FREE.propertyLimit)
        assertTrue(PlanTier.PRO.propertyLimit > 1_000_000)
    }

    @Test
    fun `a new subscription is free and unpaid`() {
        val fresh = Subscription()
        assertEquals(PlanTier.FREE, fresh.tier)
        assertFalse(fresh.isPaid)
        assertFalse(fresh.cancelAtPeriodEnd)
    }

    @Test
    fun `a corrupt stored tier degrades to free rather than granting pro`() {
        val tampered = Subscription(planTier = "PLATINUM_UNLIMITED")
        assertEquals(
            "an unreadable tier must never be treated as paid",
            PlanTier.FREE, tampered.tier
        )
        assertFalse(tampered.isPaid)
    }

    @Test
    fun `cancelling keeps the tier until the period ends`() {
        val cancelled = Subscription(
            planTier = PlanTier.PRO.name,
            cancelAtPeriodEnd = true
        )
        assertTrue("access must survive to the end of a paid period", cancelled.isPaid)
        assertTrue(cancelled.cancelAtPeriodEnd)
    }

    // ---------------------------------------------------------------- prices

    @Test
    fun `plans price from minor units with no floating point drift`() {
        assertEquals("$12.00", SubscriptionPlan.PRO_MONTHLY.displayPrice)
        assertEquals("$120.00", SubscriptionPlan.PRO_YEARLY.displayPrice)
        assertEquals(1_200, SubscriptionPlan.PRO_MONTHLY.priceMinor)
        assertEquals(12_000, SubscriptionPlan.PRO_YEARLY.priceMinor)
    }

    @Test
    fun `the annual plan is cheaper per month than the monthly one`() {
        val monthlyOverAYear = SubscriptionPlan.PRO_MONTHLY.priceMinor * 12
        assertTrue(
            "an annual plan that costs more than paying monthly is a pricing bug",
            SubscriptionPlan.PRO_YEARLY.priceMinor < monthlyOverAYear
        )
    }

    @Test
    fun `every plan is addressable by id`() {
        listOf(
            SubscriptionPlan.FREE,
            SubscriptionPlan.PRO_MONTHLY,
            SubscriptionPlan.PRO_YEARLY
        ).forEach { plan ->
            assertEquals(plan.id, SubscriptionPlan.byId(plan.id)?.id)
        }
        assertEquals(null, SubscriptionPlan.byId("plan_does_not_exist"))
    }

    @Test
    fun `the free plan carries no price`() {
        assertEquals(0, SubscriptionPlan.FREE.priceMinor)
        assertEquals(2, SubscriptionPlan.FREE.propertyLimit)
    }
}
