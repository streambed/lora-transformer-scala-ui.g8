/*
 * Copyright (c) Cisco Inc, 2018
 */

package com.github.huntc.fdp.soilstate

/**
  * A service that interprets LoRaWAN packets as soil moisture/temperature
  * observations having decrypted them, and then transforms them into their
  * non LoRaWAN-domain model representation, encrypt and, finally, re-publish
  * them as new events. Start with [[com.github.huntc.fdp.soilstate.transformer.SoilstateTransformer]]
  * and [[com.github.huntc.fdp.soilstate.transformer.SoilstateMetaFilter]].
  */
package object transformer {}
