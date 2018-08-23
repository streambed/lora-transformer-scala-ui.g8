package $organization;format="package"$.$deviceType;format="camel"$

/**
  * A service that interprets LoRaWAN packets as $deviceType$
  * observations having decrypted them, and then transforms them into their
  * non LoRaWAN-domain model representation, encrypt and, finally, re-publish
  * them as new events. Start with [[$organization;format="package"$.$deviceType;format="package"$.transformer.$deviceType;format="Camel"$Transformer]]
  * and [[$organization;format="package"$.$deviceType;format="package"$.transformer.$deviceType;format="Camel"$MetaFilter]].
  */
package object transformer {}
