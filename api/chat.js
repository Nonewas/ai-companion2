export default async function handler(req, res) {
  if (req.method !== 'POST') {
    return res.status(405).json({ reply: 'Only POST allowed' });
  }

  try {
    const userMessage = req.body.message;
    const history = req.body.history || [];
    const image = req.body.image;
    const key = process.env.GEMINI_API_KEY;

    const contents = history.map(h => ({
      role: h.role === 'model' ? 'model' : 'user',
      parts: [{ text: h.text }]
    }));

    const currentParts = [];
    if (image) {
      currentParts.push({
        inlineData: {
          mimeType: 'image/jpeg',
          data: image
        }
      });
    }
    currentParts.push({ text: userMessage });

    contents.push({ role: 'user', parts: currentParts });

    const response = await fetch(
      `https://generativelanguage.googleapis.com/v1beta/models/gemini-3.6-flash:generateContent?key=${key}`,
      {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ contents })
      }
    );

    const data = await response.json();
    const reply = data.candidates?.[0]?.content?.parts?.[0]?.text || "Sorry, I didn't get that.";

    res.status(200).json({ reply });
  } catch (err) {
    console.error(err);
    res.status(500).json({ reply: 'Error talking to AI.' });
  }
}