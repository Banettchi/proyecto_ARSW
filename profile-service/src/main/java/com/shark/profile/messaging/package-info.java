/**
 * Si profile-service está caído cuando se publica un evento, RabbitMQ lo retiene en la cola 
 * (es durable) hasta que profile-service vuelva a levantarse y lo consuma. Si profile-service 
 * SÍ está arriba pero falla al procesar un evento puntual (ej: error de base de datos temporal),
 * el mensaje va a la DLQ tras el nack, sin bloquear los demás mensajes de la cola ni perder 
 * el evento — queda disponible para reprocesamiento manual o automatizado desde la DLQ.
 */
package com.shark.profile.messaging;
