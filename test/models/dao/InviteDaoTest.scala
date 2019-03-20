package models.dao

import java.time.LocalDateTime

import models.NamedEntity
import models.invite.Invite
import org.scalacheck.Gen
import testutils.fixture.{GroupFixture, InviteFixture}
import testutils.generator.InviteGenerator

/**
  * Test for invite dao.
  */
class InviteDaoTest extends BaseDaoTest with InviteFixture with GroupFixture with InviteGenerator {

  private val dao = inject[InviteDao]

  "getList" should {
    "return list of events" in {
      val result = wait(dao.getList())

      result.total mustBe Invites.length
      result.data mustBe Invites
    }
  }

  "findByCode" should {
    "find invite by code" in {
      forAll(Gen.oneOf(Invites), Gen.oneOf(Some("not existed code"), None)) { (invite: Invite, code: Option[String]) =>
        val result = wait(dao.findByCode(code.getOrElse(invite.code)))

        result mustBe (if (code.isEmpty) Some(invite) else None)
      }
    }
  }

  "create" should {
    "create invites" in {
      forAll(inviteArb.arbitrary, Gen.someOf(Groups.map(_.id))) { (invite: Invite, groupIds: Seq[Long]) =>
        whenever(wait(dao.findByCode(invite.code)).isEmpty) {
          val groupsSet = groupIds.map(NamedEntity(_)).toSet

          val created = wait(dao.create(invite.copy(groups = groupsSet)))
          val fromDb = wait(dao.findByCode(invite.code))

          fromDb mustBe defined
          fromDb.get mustBe created
        }
      }
    }
  }

  "activate" should {
    "activate invite" in {
      forAll { time: LocalDateTime =>
        val invite = Invites(0)

        wait(dao.activate(invite.code, time))

        val fromDb = wait(dao.findByCode(invite.code)).get

        fromDb.activationTime mustBe Some(time)
      }
    }
  }
}
