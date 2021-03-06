/*
*************************************************************************************
* Copyright 2011 Normation SAS
*************************************************************************************
*
* This program is free software: you can redistribute it and/or modify
* it under the terms of the GNU Affero General Public License as
* published by the Free Software Foundation, either version 3 of the
* License, or (at your option) any later version.
*
* In accordance with the terms of section 7 (7. Additional Terms.) of
* the GNU Affero GPL v3, the copyright holders add the following
* Additional permissions:
* Notwithstanding to the terms of section 5 (5. Conveying Modified Source
* Versions) and 6 (6. Conveying Non-Source Forms.) of the GNU Affero GPL v3
* licence, when you create a Related Module, this Related Module is
* not considered as a part of the work and may be distributed under the
* license agreement of your choice.
* A "Related Module" means a set of sources files including their
* documentation that, without modification of the Source Code, enables
* supplementary functions or services in addition to those offered by
* the Software.
*
* This program is distributed in the hope that it will be useful,
* but WITHOUT ANY WARRANTY; without even the implied warranty of
* MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
* GNU Affero General Public License for more details.
*
* You should have received a copy of the GNU Affero General Public License
* along with this program. If not, see <http://www.gnu.org/licenses/agpl.html>.
*
*************************************************************************************
*/

package com.normation.rudder.batch

import com.normation.cfclerk.services.UpdateTechniqueLibrary
import com.normation.rudder.domain.Constants.TECHLIB_MINIMUM_UPDATE_INTERVAL
import net.liftweb.actor.{LiftActor, LAPinger}
import net.liftweb.common.Loggable
import com.normation.eventlog.EventActor
import com.normation.rudder.domain.eventlog.RudderEventActor
import org.joda.time.DateTime
import com.normation.utils.StringUuidGenerator
import com.normation.eventlog.ModificationId
import net.liftweb.common.Full
import net.liftweb.common.EmptyBox
import java.io.File

case class StartLibUpdate(actor: EventActor)

/**
 * A class that periodically check if the Technique Library was updated.
 *
 * updateInterval has a special semantic:
 * - for 0 or a negative value, does not start the updater
 * - for updateInterval between 1 and a minimum value, use the minimum value
 * - else, use the given value.
 */
class CheckTechniqueLibrary(
    policyPackageUpdater: UpdateTechniqueLibrary
  , asyncDeploymentAgent: AsyncDeploymentAgent
  , uuidGen             : StringUuidGenerator
  , updateInterval      : Int // in minutes
) extends Loggable {

  private val propertyName = "rudder.batch.techniqueLibrary.updateInterval"

  //start batch
  if(updateInterval < 1) {
    logger.info(s"Disable automatic Technique library updates since property ${propertyName} is 0 or negative")
  } else {
    logger.trace("***** starting Technique Library Update batch *****")
    val actor = new LAUpdateTechLibManager
    // Do not run at start up, as it may have been done in startup check already. Only register next run
    LAPinger.schedule(actor, StartLibUpdate(RudderEventActor), updateInterval*1000L*60)
  }

  ////////////////////////////////////////////////////////////////
  //////////////////// implementation details ////////////////////
  ////////////////////////////////////////////////////////////////

  private class LAUpdateTechLibManager extends LiftActor with Loggable {
    updateManager =>

    private[this] val isAutomatic = updateInterval > 0
    private[this] val realUpdateInterval = {
      if(updateInterval < TECHLIB_MINIMUM_UPDATE_INTERVAL && isAutomatic) {
        logger.warn(s"Value '${updateInterval}' for ${propertyName} is too small, using '${TECHLIB_MINIMUM_UPDATE_INTERVAL}'")
        TECHLIB_MINIMUM_UPDATE_INTERVAL
      } else {
        updateInterval
      }
    }

    override protected def messageHandler = {
      //
      //Ask for a new dynamic group update
      //
      case StartLibUpdate(actor) =>
        //schedule next update if it's automatic, in minutes
        if (isAutomatic) {
          LAPinger.schedule(this, StartLibUpdate(actor), realUpdateInterval*1000L*60)
        } // don't need an else part : we have to do nothing and the else return Unit

        logger.trace("***** Start a new update")
        policyPackageUpdater.update(ModificationId(uuidGen.newUuid), actor, Some(s"Automatic batch update at ${DateTime.now}"))

      case msg =>
        logger.error(s"Automatic Technique library updater can't handle that message: ${msg}")
    }
  }
}

