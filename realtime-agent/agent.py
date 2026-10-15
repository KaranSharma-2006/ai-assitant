import asyncio
import os
import httpx

from dotenv import load_dotenv

from livekit.agents import (
    Agent,
    AgentServer,
    AgentSession,
    JobContext,
    cli,
    inference,
)

from livekit.plugins import synthesia


load_dotenv(".env.local")


SPRING_AVATAR_URL = os.getenv(
    "SPRING_AVATAR_URL",
    "http://localhost:8080/api/avatar/ask"
)

AVATAR_ID = os.environ["SYNTHESIA_AVATAR_ID"]
VOICE_ID = os.environ["CARTESIA_VOICE_ID"]

server = AgentServer()

# Call the Spring Brain
async def process_question(
        session: AgentSession,
        question: str):

    print("USER:", question)

    try:
        async with httpx.AsyncClient() as client:

            response = await client.post(
                SPRING_AVATAR_URL,
                json={
                    "message": question
                },
                timeout=60.0
            )


            response.raise_for_status()

            answer = response.json()["answer"]

            print("SPRING:", answer)

            session.say(
                answer,
                allow_interruptions=True
            )

    except Exception as error:

        print("ERROR:", error)

        session.say(
            "Sorry, but karan sharma abhi bhi problem hai"
        )

@server.rtc_session()
async def entrypoint(ctx: JobContext):

    await ctx.connect()

    session = AgentSession(

        stt=inference.STT(
            model="deepgram/nova-3",
            language="multi"
        ),

        tts=inference.TTS(
            model="cartesia/sonic-3.6",
            voice=os.environ["CARTESIA_VOICE_ID"],
            language="hi"
        )
    )

    @session.on("user_input_transcribed")
    def on_user_input_transcribed(event):

        if event.is_final and event.transcript.strip():
            asyncio.create_task(process_question(session, event.transcript))

    print("SYNTHESIA AVATAR ID:", AVATAR_ID)

    avatar = synthesia.AvatarSession(synthesia.AvatarConfig(avatar_ids=[AVATAR_ID]))

    await avatar.start(session, room=ctx.room)

    await session.start(
        agent=Agent(
            instructions="""
            You are the realtime voice adapter
            for the Coder Army AI avatar.

            Do not generate answers yourself.

            The Spring Boot service is responsible
            for all AI reasoning and knowledge.
            """
        ),
        room=ctx.room
    )

if __name__ == "__main__":
    cli.run_app(server)