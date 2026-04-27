"""All v1 routers, aggregated."""
from __future__ import annotations

from fastapi import APIRouter

from app.api.v1 import auth, consumption, fasting, foods, openfoodfacts

router = APIRouter(prefix="/api/v1")
router.include_router(auth.router)
router.include_router(foods.router)
router.include_router(consumption.router)
router.include_router(fasting.router)
router.include_router(openfoodfacts.router)
